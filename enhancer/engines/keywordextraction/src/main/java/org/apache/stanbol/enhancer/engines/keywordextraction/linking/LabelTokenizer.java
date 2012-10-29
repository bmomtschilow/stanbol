package org.apache.stanbol.enhancer.engines.keywordextraction.linking;

import org.apache.stanbol.enhancer.engines.keywordextraction.engine.KeywordLinkingEngine;

/**
 * Interface used by the {@link KeywordLinkingEngine} to tokenize labels of
 * Entities suggested by the EntitySearcher
 *
 */
public interface LabelTokenizer {

    /**
     * Key used to configure the languages supported for tokenizing labels.
     * If not present the assumption is that the tokenizer supports all languages.
     */
    String SUPPORTED_LANUAGES = "enhancer.engines.keywordextraction.labeltokenizer.languages";
    
    
    /**
     * Tokenizes the parsed label in the parsed language
     * @param label the label
     * @param language the language of the lable or <code>null</code> if
     * not known
     * @return the tokenized label
     */
    String[] tokenize(String label, String language);
    
}
