# CoreNLP2FoLiA
Extend stanfor CoreNLP http://stanfordnlp.github.io/CoreNLP/ to support exporting FoLiA format (a rich XML-based format for linguistic annotation) https://proycon.github.io/folia/.

You can use FLAT http://applejack.science.ru.nl/languagemachines/software/flat/ to edit FoLiA file. And you can use MTAS https://meertensinstituut.github.io/mtas/ to index and store FoLiA file to Solr.

# Usage
Copy the "StanfordCoreNLPFolia.jar" file to the root of StanfordCoreNLP folder(Beside stanford-corenlp-3.7.0.jar).
You can use the same CoreNLP command line commands, after replacing main class name from "StanfordCoreNLP" to "StanfordCoreNLPFolia".

* Example:

java -cp "*" -Xmx1g edu.stanford.nlp.pipeline.StanfordCoreNLPFolia -file input.txt.

check http://stanfordnlp.github.io/CoreNLP/cmdline.html for more command line details.
