//
// StanfordCoreNLP -- a suite of NLP tools.
// Copyright (c) 2009-2011 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//

package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.FileSequentialCollection;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;
// import static edu.stanford.nlp.util.logging.Redwood.Util.*;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;



/**
 * This is a pipeline that takes in a string and returns various analyzed
 * linguistic forms.
 * The String is tokenized via a tokenizer (using a TokenizerAnnotator), and
 * then other sequence model style annotation can be used to add things like
 * lemmas, POS tags, and named entities.  These are returned as a list of CoreLabels.
 * Other analysis components build and store parse trees, dependency graphs, etc.
 * <p>
 * This class is designed to apply multiple Annotators
 * to an Annotation.  The idea is that you first
 * build up the pipeline by adding Annotators, and then
 * you take the objects you wish to annotate and pass
 * them in and get in return a fully annotated object.
 * At the command-line level you can, e.g., tokenize text with StanfordCoreNLP with a command like:
 * <br/><pre>
 * java edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit -file document.txt
 * </pre><br/>
 * Please see the package level javadoc for sample usage
 * and a more complete description.
 * <p>
 * The main entry point for the API is StanfordCoreNLPFolia.process() .
 * <p>
 * <i>Implementation note:</i> There are other annotation pipelines, but they
 * don't extend this one. Look for classes that implement Annotator and which
 * have "Pipeline" in their name.
 *
 * @author Jenny Finkel
 * @author Anna Rafferty
 * @author Christopher Manning
 * @author Mihai Surdeanu
 * @author Steven Bethard
 * @author A.Atef
 */

public class StanfordCoreNLPFolia extends StanfordCoreNLP  {

    private Properties properties;

    /** A logger for this class */
    private static final Redwood.RedwoodChannels logger = Redwood.channels(StanfordCoreNLPFolia.class);



    /**
     * Construct a basic pipeline. The Properties will be used to determine
     * which annotators to create, and a default AnnotatorPool will be used
     * to create the annotators.
     *
     */
    public StanfordCoreNLPFolia(Properties props)  {
        super(props, (props == null || PropertiesUtils.getBool(props, "enforceRequirements", true)));
        this.properties = super.getProperties();

    }


    //
    // @Override-able methods to change pipeline behavior
    //


    /**
     * Create an outputter to be passed into {@link StanfordCoreNLPFolia#processFiles(String, Collection, int, Properties, BiConsumer, BiConsumer, OutputFormat)}.
     *
     * @param properties The properties file to use.
     * @param outputOptions The means of creating output options
     *
     * @return A consumer that can be passed into the processFiles method.
     */
    protected static BiConsumer<Annotation, OutputStream> createOutputter(Properties properties, AnnotationOutputter.Options outputOptions) {

        final OutputFormat outputFormat =
                OutputFormat.valueOf(properties.getProperty("outputFormat", DEFAULT_OUTPUT_FORMAT).toUpperCase());

        final String serializerClass = properties.getProperty("serializer", GenericAnnotationSerializer.class.getName());
        final String outputSerializerClass = properties.getProperty("outputSerializer", serializerClass);
        final String outputSerializerName = (serializerClass.equals(outputSerializerClass))? "serializer":"outputSerializer";

        return (Annotation annotation, OutputStream fos) -> {
            try {
                switch (outputFormat) {
                    case XML: {
                        logger.info("Using FoLiA XML Format.");
                        AnnotationOutputter outputter = MetaClass.create("edu.stanford.nlp.pipeline.FoliaOutputter").createInstance();
                        outputter.print(annotation, fos, outputOptions);
                        break;
                    }
                    case JSON: {
                        new JSONOutputter().print(annotation, fos, outputOptions);
                        break;
                    }
                    case CONLL: {
                        new CoNLLOutputter().print(annotation, fos, outputOptions);
                        break;
                    }
                    case TEXT: {
                        new TextOutputter().print(annotation, fos, outputOptions);
                        break;
                    }
                    case SERIALIZED: {
                        if (outputSerializerClass != null) {
                          //  AnnotationSerializer outputSerializer = loadSerializer(outputSerializerClass, outputSerializerName, properties);
                          //  outputSerializer.write(annotation, fos);
                        }
                        break;
                    }
                    case CONLLU:
                        new CoNLLUOutputter().print(annotation, fos, outputOptions);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown output format " + outputFormat);
                }
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        };
    }

    /**
     * Process a collection of files.
     *
     * @param base The base input directory to process from.
     * @param files The files to process.
     * @param numThreads The number of threads to annotate on.
     *
     * @throws IOException
     */
    public void processFiles(String base, final Collection<File> files, int numThreads) throws IOException {

        AnnotationOutputter.Options options = AnnotationOutputter.getOptions(this);
        StanfordCoreNLP.OutputFormat outputFormat = StanfordCoreNLP.OutputFormat.valueOf(properties.getProperty("outputFormat", DEFAULT_OUTPUT_FORMAT).toUpperCase());

        processFiles(base, files, numThreads, properties, this::annotate, createOutputter(properties, options), outputFormat);

    }

    /**
     * A common method for processing a set of files, used in both {@link StanfordCoreNLP} as well as
     * {@link StanfordCoreNLPClient}.
     *
     * @param base The base input directory to process from.
     * @param files The files to process.
     * @param numThreads The number of threads to annotate on.
     * @param properties The properties file to use during annotation.
     *                   This should match the properties file used in the implementation of the annotate function.
     * @param annotate The function used to annotate a document.
     * @param print The function used to print a document.
     * @throws IOException
     */
    protected static void processFiles(String base, final Collection<File> files, int numThreads,
                                       Properties properties, BiConsumer<Annotation, Consumer<Annotation>> annotate,
                                       BiConsumer<Annotation, OutputStream> print,
                                       OutputFormat outputFormat) throws IOException {
        // List<Runnable> toRun = new LinkedList<>();


        // Process properties here
        final String baseOutputDir = properties.getProperty("outputDirectory", ".");
        final String baseInputDir = properties.getProperty("inputDirectory", base);

        // Set of files to exclude
        final String excludeFilesParam = properties.getProperty("excludeFiles");
        final Set<String> excludeFiles = new HashSet<>();
        if (excludeFilesParam != null) {
            Iterable<String> lines = IOUtils.readLines(excludeFilesParam);
            for (String line:lines) {
                String name = line.trim();
                if (!name.isEmpty()) excludeFiles.add(name);
            }
        }

        //(file info)
        final String serializerClass = properties.getProperty("serializer", GenericAnnotationSerializer.class.getName());
        final String inputSerializerClass = properties.getProperty("inputSerializer", serializerClass);
        final String inputSerializerName = (serializerClass.equals(inputSerializerClass))? "serializer":"inputSerializer";

        String defaultExtension;
        switch (outputFormat) {
            case XML: defaultExtension = ".xml"; break;
            case JSON: defaultExtension = ".json"; break;
            case CONLL: defaultExtension = ".conll"; break;
            case CONLLU: defaultExtension = ".conllu"; break;
            case TEXT: defaultExtension = ".out"; break;
            case SERIALIZED: defaultExtension = ".ser.gz"; break;
            default: throw new IllegalArgumentException("Unknown output format " + outputFormat);
        }

        final String extension = properties.getProperty("outputExtension", defaultExtension);
        final boolean replaceExtension = Boolean.parseBoolean(properties.getProperty("replaceExtension", "false"));
        final boolean continueOnAnnotateError = Boolean.parseBoolean(properties.getProperty("continueOnAnnotateError", "false"));

        final boolean noClobber = Boolean.parseBoolean(properties.getProperty("noClobber", "false"));
        // final boolean randomize = Boolean.parseBoolean(properties.getProperty("randomize", "false"));

        final MutableInteger totalProcessed = new MutableInteger(0);
        final MutableInteger totalSkipped = new MutableInteger(0);
        final MutableInteger totalErrorAnnotating = new MutableInteger(0);

        //for each file...
        for (final File file : files) {
            // Determine if there is anything to be done....
            if (excludeFiles.contains(file.getName())) {
                logger.err("Skipping excluded file " + file.getName());
                totalSkipped.incValue(1);
                continue;
            }

            //--Get Output File Info
            //(filename)
            String outputDir = baseOutputDir;
            if (baseInputDir != null) {
                // Get input file name relative to base
                String relDir = file.getParent().replaceFirst(Pattern.quote(baseInputDir), "");
                outputDir = outputDir + File.separator + relDir;
            }
            // Make sure output directory exists
            new File(outputDir).mkdirs();
            String outputFilename = new File(outputDir, file.getName()).getPath();
            if (replaceExtension) {
                int lastDot = outputFilename.lastIndexOf('.');
                // for paths like "./zzz", lastDot will be 0
                if (lastDot > 0) {
                    outputFilename = outputFilename.substring(0, lastDot);
                }
            }
            // ensure we don't make filenames with doubled extensions like .xml.xml
            if (!outputFilename.endsWith(extension)) {
                outputFilename += extension;
            }
            // normalize filename for the upcoming comparison
            outputFilename = new File(outputFilename).getCanonicalPath();

            //--Conditions For Skipping The File
            // TODO this could fail if there are softlinks, etc. -- need some sort of sameFile tester
            //      Java 7 will have a Files.isSymbolicLink(file) method
            if (outputFilename.equals(file.getCanonicalPath())) {
                logger.err("Skipping " + file.getName() + ": output file " + outputFilename + " has the same filename as the input file -- assuming you don't actually want to do this.");
                totalSkipped.incValue(1);
                continue;
            }
            if (noClobber && new File(outputFilename).exists()) {
                logger.err("Skipping " + file.getName() + ": output file " + outputFilename + " as it already exists.  Don't use the noClobber option to override this.");
                totalSkipped.incValue(1);
                continue;
            }

            final String finalOutputFilename = outputFilename;

            //register a task...
            //catching exceptions...
            try {
                // Check whether this file should be skipped again
                if (noClobber && new File(finalOutputFilename).exists()) {
                    logger.err("Skipping " + file.getName() + ": output file " + finalOutputFilename + " as it already exists.  Don't use the noClobber option to override this.");
                    synchronized (totalSkipped) {
                        totalSkipped.incValue(1);
                    }
                    return;
                }

                logger.info("Processing file " + file.getAbsolutePath() + " ... writing to " + finalOutputFilename);

                //--Process File
                Annotation annotation = null;
                if (file.getAbsolutePath().endsWith(".ser.gz")) {
                    // maybe they want to continue processing a partially processed annotation
                    try {
                        // Create serializers
                        if (inputSerializerClass != null) {
                            AnnotationSerializer inputSerializer = loadSerializer(inputSerializerClass, inputSerializerName, properties);
                            InputStream is = new BufferedInputStream(new FileInputStream(file));
                            Pair<Annotation, InputStream> pair = inputSerializer.read(is);
                            pair.second.close();
                            annotation = pair.first;
                            IOUtils.closeIgnoringExceptions(is);
                        } else {
                            annotation = IOUtils.readObjectFromFile(file);
                        }
                    } catch (IOException e) {
                        // guess that's not what they wanted
                        // We hide IOExceptions because ones such as file not
                        // found will be thrown again in a moment.  Note that
                        // we are intentionally letting class cast exceptions
                        // and class not found exceptions go through.
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }

                //(read file)
                if (annotation == null) {
                    String encoding = properties.getProperty("encoding", "UTF-8");
                    String text = IOUtils.slurpFile(file.getAbsoluteFile(), encoding);
                    annotation = new Annotation(text);
                }

                Timing timing = new Timing();
                annotate.accept(annotation, finishedAnnotation -> {
                    timing.done(logger, "Annotating file " + file.getAbsoluteFile());
                    Throwable ex = finishedAnnotation.get(CoreAnnotations.ExceptionAnnotation.class);
                    if (ex == null) {
                        //--Output File
                        try {
                            OutputStream fos = new BufferedOutputStream(new FileOutputStream(finalOutputFilename));
                            print.accept(finishedAnnotation, fos);
                            fos.close();
                        } catch(IOException e) {
                            throw new RuntimeIOException(e);
                        }

                        synchronized (totalProcessed) {
                            totalProcessed.incValue(1);
                            if (totalProcessed.intValue() % 1000 == 0) {
                                logger.info("Processed " + totalProcessed + " documents");
                            }
                        }
                    } else if (continueOnAnnotateError) {
                        // Error annotating but still wanna continue
                        // (maybe in the middle of long job and maybe next one will be okay)
                        logger.err("Error annotating " + file.getAbsoluteFile() + ": " + ex);
                        synchronized (totalErrorAnnotating) {
                            totalErrorAnnotating.incValue(1);
                        }

                    } else {
                        throw new RuntimeException("Error annotating " + file.getAbsoluteFile(), ex);
                    }
                });


            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }

    /*
    if (randomize) {
      log("Randomly shuffling input");
      Collections.shuffle(toRun);
    }
    log("Ready to process: " + toRun.size() + " files, skipped " + totalSkipped + ", total " + nFiles);
    //--Run Jobs
    if(numThreads == 1){
      for(Runnable r : toRun){ r.run(); }
    } else {
      Redwood.Util.threadAndRun("StanfordCoreNLP <" + numThreads + " threads>", toRun, numThreads);
    }
    log("Processed " + totalProcessed + " documents");
    log("Skipped " + totalSkipped + " documents, error annotating " + totalErrorAnnotating + " documents");
    */
    }

    private static AnnotationSerializer loadSerializer(String serializerClass, String name, Properties properties) {
        AnnotationSerializer serializer; // initialized below
        try {
            // Try loading with properties
            serializer = ReflectionLoading.loadByReflection(serializerClass, name, properties);
        } catch (ReflectionLoading.ReflectionLoadingException ex) {
            // Try loading with just default constructor
            serializer = ReflectionLoading.loadByReflection(serializerClass);
        }
        return serializer;
    }

    /**
     * Displays the output of all annotators in XML format.
     * @param annotation Contains the output of all annotators
     * @param os The output stream
     * @throws IOException
     */
    public void xmlPrint(Annotation annotation, OutputStream os) throws IOException {
        try {
            Class clazz = Class.forName("edu.stanford.nlp.pipeline.XMLOutputter");
            Method method = clazz.getMethod("xmlPrint", Annotation.class, OutputStream.class, StanfordCoreNLPFolia.class);
            method.invoke(null, annotation, os, this);
        } catch (NoSuchMethodException | IllegalAccessException | ClassNotFoundException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() throws IOException {
        Timing tim = new Timing();

        StanfordRedwoodConfiguration.minimalSetup();

        // multithreading thread count
        String numThreadsString = (this.properties == null) ? null : this.properties.getProperty("threads");
        int numThreads = 1;
        try{
            if (numThreadsString != null) {
                numThreads = Integer.parseInt(numThreadsString);
            }
        } catch(NumberFormatException e) {
            logger.err("-threads [number]: was not given a valid number: " + numThreadsString);
        }

        // blank line after all the loading statements to make output more readable
        logger.info("");

        //
        // Process one file or a directory of files
        //
        if (properties.containsKey("file") || properties.containsKey("textFile")) {
            String fileName = properties.getProperty("file");
            if (fileName == null) {
                fileName = properties.getProperty("textFile");
            }
            Collection<File> files = new FileSequentialCollection(new File(fileName), properties.getProperty("extension"), true);

            this.processFiles(null, files, numThreads);
        }

        //
        // Process a list of files
        //
        else if (properties.containsKey("filelist")){
            String fileName = properties.getProperty("filelist");
            Collection<File> inputFiles = readFileList(fileName);
            Collection<File> files = new ArrayList<>(inputFiles.size());
            for (File file : inputFiles) {
                if (file.isDirectory()) {
                    files.addAll(new FileSequentialCollection(new File(fileName), properties.getProperty("extension"), true));
                } else {
                    files.add(file);
                }
            }
            this.processFiles(null, files, numThreads);
        }

        //
        // Run the interactive shell
        //
        else {
           // shell(this);
        }

        /*
        if (TIME) {
            logger.info(""); // puts blank line in logging output
            logger.info(this.timingInformation());
            logger.info("Pipeline setup: " +
                    Timing.toSecondsString(pipelineSetupTime) + " sec.");
            logger.info("Total time for StanfordCoreNLP pipeline: " +
                    Timing.toSecondsString(pipelineSetupTime + tim.report()) + " sec.");
        }
        */

    }



    /**
     * This can be used just for testing or for command-line text processing.
     * This runs the pipeline you specify on the
     * text in the file that you specify and sends some results to stdout.
     * The current code in this main method assumes that each line of the file
     * is to be processed separately as a single sentence.
     * <p>
     * Example usage:<br>
     * java -mx6g edu.stanford.nlp.pipeline.StanfordCoreNLPFolia properties
     *
     * @param args List of required properties
     * @throws java.io.IOException If IO problem
     * @throws ClassNotFoundException If class loading problem
     */
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        //
        // process the arguments
        //
        // Extract all the properties from the command line.
        // As well as command-line properties, the processor will search for the properties file in the classpath
        Properties props = new Properties();
        if (args.length > 0) {
            logger.info("----> StanfordCoreNLPFolia args.length > 0");
            props = StringUtils.argsToProperties(args);
            boolean hasH = props.containsKey("h");
            boolean hasHelp = props.containsKey("help");
            if (hasH || hasHelp) {
                String helpValue = hasH ? props.getProperty("h") : props.getProperty("help");
                printHelp(System.err, helpValue);
                return;
            }
        }
        logger.info("----> StanfordCoreNLPFolia Main");
        // Run the pipeline
        new StanfordCoreNLPFolia(props).run();
    }

}
