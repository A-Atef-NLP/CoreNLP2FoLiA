package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;
import nu.xom.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An outputter to Folia format.
 * Created by A.Atef on 1/13/17.
 */
public class FoliaOutputter extends XMLOutputter {

    /**
     * A logger for this class
     */
    private static final Redwood.RedwoodChannels logger = Redwood.channels(FoliaOutputter.class);

    // the namespace is set in the XSLT file
    private static final String NAMESPACE_URI = "http://ilk.uvt.nl/folia";
    private static final String STYLESHEET_NAME = "folia2html.xsl";

    public FoliaOutputter() {
        logger.info("Initialize FoliaOutputter");
    }


    protected static void addFoliaMetadata(Element tokenElement) {

        Element metadata = new Element("metadata", NAMESPACE_URI);

        Element annotations = new Element("annotations", NAMESPACE_URI);

        Element tokenAnnotation = new Element("token-annotation", NAMESPACE_URI);
        tokenAnnotation.addAttribute(new Attribute("annotator", "CoreNLP"));
        tokenAnnotation.addAttribute(new Attribute("annotatortype", "auto"));
        Element phonologicalAnnotation = new Element("phonological-annotation", NAMESPACE_URI);
        phonologicalAnnotation.addAttribute(new Attribute("annotator", "CoreNLP"));
        phonologicalAnnotation.addAttribute(new Attribute("annotatortype", "auto"));
        Element morphologicalAnnotation = new Element("morphological-annotation", NAMESPACE_URI);
        morphologicalAnnotation.addAttribute(new Attribute("annotator", "CoreNLP"));
        morphologicalAnnotation.addAttribute(new Attribute("annotatortype", "auto"));
        Element posAnnotation = new Element("pos-annotation", NAMESPACE_URI);
        posAnnotation.addAttribute(new Attribute("annotator", "CoreNLP"));
        posAnnotation.addAttribute(new Attribute("annotatortype", "auto"));
        Element lemmaAnnotation = new Element("lemma-annotation", NAMESPACE_URI);
        lemmaAnnotation.addAttribute(new Attribute("annotator", "CoreNLP"));
        lemmaAnnotation.addAttribute(new Attribute("annotatortype", "auto"));
        Element entityAnnotation = new Element("entity-annotation", NAMESPACE_URI);
        entityAnnotation.addAttribute(new Attribute("annotator", "CoreNLP"));
        entityAnnotation.addAttribute(new Attribute("annotatortype", "auto"));

        annotations.appendChild(tokenAnnotation);
        annotations.appendChild(phonologicalAnnotation);
        annotations.appendChild(morphologicalAnnotation);
        annotations.appendChild(posAnnotation);
        annotations.appendChild(lemmaAnnotation);
        annotations.appendChild(entityAnnotation);

        Element meta = new Element("meta", NAMESPACE_URI);
        meta.appendChild("en");
        meta.addAttribute(new Attribute("id", "language"));

        metadata.appendChild(annotations);
        metadata.appendChild(meta);

        tokenElement.appendChild(metadata);

    }


    /**
     * Converts the given annotation to an XML document using the specified options
     */
    public static Document annotationToDoc(Annotation annotation, Options options) {
        //
        // create the XML document with the root node pointing to the namespace URL
        //
        Element root = new Element("FoLiA", NAMESPACE_URI);
        root.addNamespaceDeclaration("xlink", "http://www.w3.org/1999/xlink");
        //Element root = new Element("FoLiA", "http://ilk.uvt.nl/folia");
        //root.addAttribute(new Attribute("xmlns:xlink", NAMESPACE_URI, "http://www.w3.org/1999/xlink"));
        root.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", "untitled"));
        root.addAttribute(new Attribute("version","1.4.0"));
        root.addAttribute(new Attribute("generator","CoreNLP2FoLiA"));

        Document xmlDoc = new Document(root);
        ProcessingInstruction pi = new ProcessingInstruction("xml-stylesheet",
                "href=\"" + STYLESHEET_NAME + "\" type=\"text/xsl\"");
        xmlDoc.insertChild(pi, 0);

        addFoliaMetadata(root);

        Element docElem = new Element("text", NAMESPACE_URI);
        root.appendChild(docElem);


        setSingleElement(docElem, "docId", NAMESPACE_URI, annotation.get(CoreAnnotations.DocIDAnnotation.class));
        setSingleElement(docElem, "docDate", NAMESPACE_URI, annotation.get(CoreAnnotations.DocDateAnnotation.class));
        setSingleElement(docElem, "docSourceType", NAMESPACE_URI, annotation.get(CoreAnnotations.DocSourceTypeAnnotation.class));
        setSingleElement(docElem, "docType", NAMESPACE_URI, annotation.get(CoreAnnotations.DocTypeAnnotation.class));
        setSingleElement(docElem, "author", NAMESPACE_URI, annotation.get(CoreAnnotations.AuthorAnnotation.class));
        setSingleElement(docElem, "location", NAMESPACE_URI, annotation.get(CoreAnnotations.LocationAnnotation.class));

        // I can not add multiple Paragraph because I don't know if CoreNLP support that or not.
        docElem.appendChild(buildParagraph(annotation, NAMESPACE_URI, "doc.p.1", options));


        //
        // add the coref graph
        //
        /*
        Map<Integer, CorefChain> corefChains =
                annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
        if (corefChains != null) {
            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
            Element corefInfo = new Element("coreference", NAMESPACE_URI);
            if (addCorefGraphInfo(options, corefInfo, sentences, corefChains, NAMESPACE_URI))
                docElem.appendChild(corefInfo);
        }
        */

        //
        // save any document-level annotations here
        //

        return xmlDoc;
    }

    /**
     * Build Paragraph XML element and its sub sentence.
     * Note: I can not add multiple Paragraph because I don't know if CoreNLP support that or not.
     * @param paragraph CoreMap element
     * @param curNS The current namespace
     * @param paragraphID paragraph ID
     * @param options export options
     * @return Paragraph as XML element
     */
    private static Element buildParagraph(CoreMap paragraph, String curNS, String paragraphID, Options options) {

        Element paragraphElement = new Element("p", curNS);

       //paragraphElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xml:id", "http://www.acme.com/schemas");
        //paragraphElement.addNamespaceDeclaration("xml", curNS);
       // paragraphElement.setNamespacePrefix("xml");

        Attribute a1 = new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace" ,paragraphID);
        //a1.setNamespace("xml", curNS);
        //a1.setLocalName("xml:id");


        paragraphElement.addAttribute(a1);


        // Include Document text
        if (options.includeText) {
            setSingleElement(paragraphElement, "t", curNS, paragraph.get(CoreAnnotations.TextAnnotation.class));
        }

        //Element sentencesElem = new Element("s", NAMESPACE_URI);

        //
        // save the info for each sentence in this doc
        //
        if (paragraph.get(CoreAnnotations.SentencesAnnotation.class) != null) {
            int sentCount = 1;
            for (CoreMap sentence : paragraph.get(CoreAnnotations.SentencesAnnotation.class)) {
                // add the sentence to the root
                paragraphElement.appendChild(buildSentence(sentence, curNS,paragraphID + ".s." + sentCount));
                sentCount++;
            }

        }

        return paragraphElement;

    }

    /**
     *
     * @param sentence CoreMap element
     * @param curNS The current namespace
     * @param sentenceID Sentence ID
     * @return Sentence as XML element
     */
    private static Element buildSentence(CoreMap sentence, String curNS, String sentenceID) {

        Element sentElem = new Element("s", curNS);
        sentElem.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace" ,sentenceID));
        Integer lineNumber = sentence.get(CoreAnnotations.LineNumberAnnotation.class);
        if (lineNumber != null) {
            sentElem.addAttribute(new Attribute("line", Integer.toString(lineNumber)));
        }

        // add the word table with all token-level annotations
        //Element wordTable = new Element("tokens", NAMESPACE_URI);
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        for (int j = 0; j < tokens.size(); j++) {
            sentElem.appendChild(buildWord(tokens.get(j), sentenceID + ".w." + (j + 1), curNS));
        }

        sentElem.appendChild(buildEntities(sentence, sentenceID + ".entities.1", curNS));

        //sentElem.appendChild(wordTable);
/*
                // add tree info
                Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);

                if(tree != null) {
                    // add the constituent tree for this sentence
                    Element parseInfo = new Element("parse", NAMESPACE_URI);
                    addConstituentTreeInfo(parseInfo, tree, options.constituentTreePrinter);
                    sentElem.appendChild(parseInfo);
                }

                SemanticGraph basicDependencies = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);

                if (basicDependencies != null) {
                    // add the dependencies for this sentence
                    Element depInfo = buildDependencyTreeInfo("basic-dependencies", sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class), tokens, NAMESPACE_URI);
                    if (depInfo != null) {
                        sentElem.appendChild(depInfo);
                    }

                    depInfo = buildDependencyTreeInfo("collapsed-dependencies", sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class), tokens, NAMESPACE_URI);
                    if (depInfo != null) {
                        sentElem.appendChild(depInfo);
                    }

                    depInfo = buildDependencyTreeInfo("collapsed-ccprocessed-dependencies", sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class), tokens, NAMESPACE_URI);
                    if (depInfo != null) {
                        sentElem.appendChild(depInfo);
                    }

                    depInfo = buildDependencyTreeInfo("enhanced-dependencies", sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class), tokens, NAMESPACE_URI);
                    if (depInfo != null) {
                        sentElem.appendChild(depInfo);
                    }

                    depInfo = buildDependencyTreeInfo("enhanced-plus-plus-dependencies", sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class), tokens, NAMESPACE_URI);
                    if (depInfo != null) {
                        sentElem.appendChild(depInfo);
                    }
                }

                // add Open IE triples
                Collection<RelationTriple> openieTriples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
                if (openieTriples != null) {
                    Element openieElem = new Element("openie", NAMESPACE_URI);
                    addTriples(openieTriples, openieElem, NAMESPACE_URI);
                    sentElem.appendChild(openieElem);
                }

                // add KBP triples
                Collection<RelationTriple> kbpTriples = sentence.get(CoreAnnotations.KBPTriplesAnnotation.class);
                if (kbpTriples != null) {
                    Element kbpElem = new Element("kbp", NAMESPACE_URI);
                    addTriples(kbpTriples, kbpElem, NAMESPACE_URI);
                    sentElem.appendChild(kbpElem);
                }

                // add the MR entities and relations
                List<EntityMention> entities = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
                List<RelationMention> relations = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
                if (entities != null && ! entities.isEmpty()){
                    Element mrElem = new Element("MachineReading", NAMESPACE_URI);
                    Element entElem = new Element("entities", NAMESPACE_URI);
                    addEntities(entities, entElem, NAMESPACE_URI);
                    mrElem.appendChild(entElem);

                    if(relations != null){
                        Element relElem = new Element("relations", NAMESPACE_URI);
                        addRelations(relations, relElem, NAMESPACE_URI, options.relationsBeam);
                        mrElem.appendChild(relElem);
                    }

                    sentElem.appendChild(mrElem);
                }

                Tree sentimentTree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
                if (sentimentTree != null) {
                    int sentiment = RNNCoreAnnotations.getPredictedClass(sentimentTree);
                    sentElem.addAttribute(new Attribute("sentimentValue", Integer.toString(sentiment)));
                    String sentimentClass = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
                    sentElem.addAttribute(new Attribute("sentiment", sentimentClass.replaceAll(" ", "")));
                }

*/
        return sentElem;

    }


    private static Element buildWord(CoreMap token, String wordID, String curNS) {
        // store the position of this word in the sentence

        Element wordInfo = new Element("w", curNS);

        wordInfo.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", wordID));

        HashMap<String, String> wordAttr = new HashMap<>();

        if (token.containsKey(CoreAnnotations.CharacterOffsetBeginAnnotation.class)) {
            wordAttr.put("offset", Integer.toString(token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class)));
        } else {
            setSingleElement(wordInfo, "t", curNS, token.get(CoreAnnotations.TextAnnotation.class));
        }
        setSingleElement(wordInfo, "t", curNS, token.get(CoreAnnotations.TextAnnotation.class), wordAttr);

        setInlineElement(wordInfo, "lemma", curNS, token.get(CoreAnnotations.LemmaAnnotation.class));

        if (token.containsKey(CoreAnnotations.PartOfSpeechAnnotation.class)) {
            setInlineElement(wordInfo, "pos", curNS, token.get(CoreAnnotations.PartOfSpeechAnnotation.class));
        }

        /* AAtef removed this. Ner is in sentance level
        if (token.containsKey(CoreAnnotations.NamedEntityTagAnnotation.class)) {
            setInlineElement(wordInfo, "NER", curNS, token.get(CoreAnnotations.NamedEntityTagAnnotation.class));
        }

        if (token.containsKey(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class)) {
            setInlineElement(wordInfo, "NormalizedNER", curNS, token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class));
        }
        */


        /* A.Atef remove below. We need to know how FoLiA will support them.

        if (token.containsKey(CoreAnnotations.SpeakerAnnotation.class)) {
            setInlineElement(wordInfo, "Speaker", curNS, token.get(CoreAnnotations.SpeakerAnnotation.class));
        }

        if (token.containsKey(TimeAnnotations.TimexAnnotation.class)) {
            Timex timex = token.get(TimeAnnotations.TimexAnnotation.class);
            Element timexElem = new Element("Timex", curNS);
            timexElem.addAttribute(new Attribute("tid", timex.tid()));
            timexElem.addAttribute(new Attribute("type", timex.timexType()));
            timexElem.appendChild(timex.value());
            wordInfo.appendChild(timexElem);
        }


        if (token.containsKey(CoreAnnotations.TrueCaseAnnotation.class)) {
            Element cur = new Element("TrueCase", curNS);
            cur.appendChild(token.get(CoreAnnotations.TrueCaseAnnotation.class));
            wordInfo.appendChild(cur);
        }
        if (token.containsKey(CoreAnnotations.TrueCaseTextAnnotation.class)) {
            Element cur = new Element("TrueCaseText", curNS);
            cur.appendChild(token.get(CoreAnnotations.TrueCaseTextAnnotation.class));
            wordInfo.appendChild(cur);
        }

        if (token.containsKey(SentimentCoreAnnotations.SentimentClass.class)) {
            Element cur = new Element("sentiment", curNS);
            cur.appendChild(token.get(SentimentCoreAnnotations.SentimentClass.class));
            wordInfo.appendChild(cur);
        }

        if (token.containsKey(CoreAnnotations.WikipediaEntityAnnotation.class)) {
            Element cur = new Element("entitylink", curNS);
            cur.appendChild(token.get(CoreAnnotations.WikipediaEntityAnnotation.class));
            wordInfo.appendChild(cur);
        }
        */ //A.Atef Remove End

//    IntTuple corefDest;
//    if((corefDest = label.get(CorefDestAnnotation.class)) != null){
//      Element cur = new Element("coref", curNS);
//      String value = Integer.toString(corefDest.get(0)) + "." + Integer.toString(corefDest.get(1));
//      cur.setText(value);
//      wordInfo.addContent(cur);
//    }


        return wordInfo;

    }


    /**
     *
     * @param sentence CoreMap element
     * @param curNS The current namespace
     * @param entitiesID Sentence ID
     * @return Sentence as XML element
     */
    private static Element buildEntities(CoreMap sentence, String entitiesID, String curNS) {

        Element entitiesElem = new Element("entities", curNS);
        entitiesElem.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", entitiesID));

        // add the word table with all token-level annotations
        //Element wordTable = new Element("tokens", NAMESPACE_URI);
        int entityCounter = 1;
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        for (int j = 0; j < tokens.size(); j++) {
            if (tokens.get(j).containsKey(CoreAnnotations.NamedEntityTagAnnotation.class)) {
                String nerValue = tokens.get(j).get(CoreAnnotations.NamedEntityTagAnnotation.class);

                if(nerValue.equalsIgnoreCase("O")) continue;

                Element entity = new Element("entity", curNS);
                entity.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", entitiesID + ".entity." + entityCounter));
                entity.addAttribute(new Attribute("class",nerValue));

                Element wref = new Element("wref", curNS);
                wref.addAttribute(new Attribute("id", entitiesID.replaceFirst(".entities.1", ".w." + (j+1))));
                wref.addAttribute(new Attribute("t",tokens.get(j).get(CoreAnnotations.TextAnnotation.class)));

                entity.appendChild(wref);
                entitiesElem.appendChild(entity);
                entityCounter++;
            }
        }

        return entitiesElem;

    }

    /**
     * Helper method for setSingleElement().  If the value is not null,
     * creates an element of the given name and namespace and adds it to the
     * tokenElement.
     *
     * @param tokenElement This is the element to which the newly created element will be added
     * @param elemName     This is the name for the new XML element
     * @param curNS        The current namespace
     * @param value        This is its value
     */
    private static void setSingleElement(Element tokenElement, String elemName, String curNS, String value, HashMap<String, String> attributes) {
        if (value != null) {
            Element cur = new Element(elemName, curNS);
            for (Map.Entry m : attributes.entrySet()) {
                cur.addAttribute(new Attribute((String) m.getKey(), (String) m.getValue()));
            }
            cur.appendChild(value);
            tokenElement.appendChild(cur);
        }
    }

    /**
     * Helper method for addWordInfo().  If the value is not null,
     * creates an element of the given name and namespace and adds it to the
     * tokenElement.
     *
     * @param tokenElement This is the element to which the newly created element will be added
     * @param elemName     This is the name for the new XML element
     * @param curNS        The current namespace
     * @param value        This is its value
     */
    private static void setSingleElement(Element tokenElement, String elemName, String curNS, String value) {
        setSingleElement(tokenElement, elemName, curNS, value, new HashMap<>());
    }

    /**
     * Helper method for addWordInfo().  If the value is not null,
     * creates an element of the given name and namespace and adds it to the
     * tokenElement.
     *
     * @param tokenElement This is the element to which the newly created element will be added
     * @param elemName     This is the name for the new XML element
     * @param curNS        The current namespace
     * @param value        This is its value
     */
    private static void setInlineElement(Element tokenElement, String elemName, String curNS, String value) {
        if (value != null) {
            Element cur = new Element(elemName, curNS);
            cur.addAttribute(new Attribute("class", value));
            if (elemName.equalsIgnoreCase("pos")) cur.addAttribute(new Attribute("head", value));
            tokenElement.appendChild(cur);
        }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void print(Annotation annotation, OutputStream os, Options options) throws IOException {

        logger.info("Print FoLiA format.");

        Document xmlDoc = annotationToDoc(annotation, options);
        Serializer ser = new Serializer(os, options.encoding);
        if (options.pretty) {
            ser.setIndent(2);
        } else {
            ser.setIndent(0);
        }
        ser.setMaxLength(0);
        ser.write(xmlDoc);
        ser.flush();
    }

}
