/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.stanbol.contenthub.search.featured;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.clerezza.rdf.core.Literal;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.SolrParams;
import org.apache.stanbol.commons.solr.managed.ManagedSolrServer;
import org.apache.stanbol.contenthub.search.featured.util.SolrContentItemConverter;
import org.apache.stanbol.contenthub.search.solr.util.SolrQueryUtil;
import org.apache.stanbol.contenthub.servicesapi.Constants;
import org.apache.stanbol.contenthub.servicesapi.search.SearchException;
import org.apache.stanbol.contenthub.servicesapi.search.featured.DocumentResult;
import org.apache.stanbol.contenthub.servicesapi.search.featured.FacetResult;
import org.apache.stanbol.contenthub.servicesapi.search.featured.FeaturedSearch;
import org.apache.stanbol.contenthub.servicesapi.search.featured.SearchResult;
import org.apache.stanbol.contenthub.servicesapi.search.related.RelatedKeyword;
import org.apache.stanbol.contenthub.servicesapi.search.related.RelatedKeywordSearchManager;
import org.apache.stanbol.contenthub.servicesapi.search.solr.SolrSearch;
import org.apache.stanbol.contenthub.servicesapi.store.StoreException;
import org.apache.stanbol.contenthub.servicesapi.store.vocabulary.SolrVocabulary;
import org.apache.stanbol.contenthub.store.solr.manager.SolrCoreManager;
import org.apache.stanbol.enhancer.servicesapi.ContentItem;
import org.apache.stanbol.enhancer.servicesapi.EnhancementException;
import org.apache.stanbol.enhancer.servicesapi.EnhancementJobManager;
import org.apache.stanbol.enhancer.servicesapi.helper.InMemoryContentItem;
import org.apache.stanbol.enhancer.servicesapi.rdf.Properties;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
@Service
public class FeaturedSearchImpl implements FeaturedSearch {

    private final static Logger log = LoggerFactory.getLogger(FeaturedSearchImpl.class);

    private static Map<String,List<String>> stopWords;

    static {
        stopWords = new HashMap<String,List<String>>();
        // TODO read stopwords from the files located in default solr core zip
        List<String> englishStopWords = Arrays.asList("i", "me", "my", "myself", "we", "our", "ours",
            "ourselves", "you", "your", "yours", "yourself", "yourselves", "he", "him", "his", "himself",
            "she", "her", "hers", "herself", "it", "its", "itself", "they", "them", "their", "theirs",
            "themselves", "what", "which", "who", "whom", "this", "that", "these", "those", "am", "is",
            "are", "was", "were", "be  ", "been", "being", "have", "has ", "had ", "having", "do", "does",
            "did ", "doing", "would", "should", "could", "ought", "a", "an", "the", "and", "but", "if", "or",
            "because", "as", "until", "while", "", "of", "at", "by", "for", "with", "about", "against",
            "between", "into", "through", "during", "before", "after", "above", "below", "to", "from", "up",
            "down", "in", "out", "on", "off", "over", "under", "again", "further", "then", "once", "here",
            "there", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more", "most",
            "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very",
            "i'm", "you're", "he's", "she's", "it's", "we're", "they're", "i've", "you've", "we've",
            "they've", "i'd", "you'd", "he'd", "she'd", "we'd", "they'd", "i'll", "you'll", "he'll",
            "she'll", "we'll", "they'll", "isn't", "aren't", "wasn't", "weren't", "hasn't", "haven't",
            "hadn't", "doesn't", "don't", "didn't", "won't", "wouldn't", "shan't", "shouldn't", "can't",
            "cannot", "couldn't", "mustn't", "let's", "that's", "who's", "what's", "here's", "there's",
            "when's", "where's", "why's", "how's", "of");
        stopWords.put("en", englishStopWords);
    }

    @Reference
    private SolrSearch solrSearch;

    @Reference
    private RelatedKeywordSearchManager relatedKeywordSearchManager;

    @Reference
    private ManagedSolrServer managedSolrServer;

    @Reference
    private EnhancementJobManager enhancementJobManager;

    private BundleContext bundleContext;

    @Activate
    public void activate(ComponentContext context) {
        this.bundleContext = context.getBundleContext();
    }

    @Override
    public SearchResult search(String queryTerm) throws SearchException {
        return search(queryTerm, null, null);
    }

    private List<FacetResult> sortFacets(List<FacetField> facetFields) {
        List<FacetResult> facets = new ArrayList<FacetResult>();
        List<FacetResult> orderedFacets = new ArrayList<FacetResult>();
        for (FacetField facetField : facetFields) {
            facets.add(new FacetResultImpl(facetField));
        }

        int annotatedFacetNum = 0;
        for (FacetResult ff : facets) {
            String facetName = ff.getName();
            if (ff.getValues() == null) {
                continue;
            } else if (SolrVocabulary.SolrFieldName.isAnnotatedEntityFacet(facetName)) {
                orderedFacets.add(annotatedFacetNum, ff);
                annotatedFacetNum++;
            } else {
                boolean inserted = false;
                for (int j = annotatedFacetNum; j < orderedFacets.size(); j++) {
                    if (facetName.compareTo(orderedFacets.get(j).getName()) < 0) {
                        orderedFacets.add(j, ff);
                        inserted = true;
                        break;
                    }
                }
                if (inserted == false) {
                    orderedFacets.add(ff);
                }
            }
        }

        return orderedFacets;
    }

    public static void main(String[] args) {
        String a = "a";
        String b = "b";
        String ab = "ab";

        System.out.println(a.compareTo(b));
        System.out.println(a.compareTo(ab));
        System.out.println(b.compareTo(ab));
    }

    @Override
    public SearchResult search(String queryTerm, String ontologyURI, String ldProgramName) throws SearchException {
        QueryResponse queryResponse = solrSearch.search(queryTerm, ldProgramName);
        return search(queryTerm, queryResponse, ontologyURI, ldProgramName);
    }

    private SearchResult search(String queryTerm,
                                QueryResponse queryResponse,
                                String ontologyURI,
                                String ldProgramName) throws SearchException {
        List<DocumentResult> resultantDocuments = new ArrayList<DocumentResult>();
        for (SolrDocument solrDocument : queryResponse.getResults()) {
            resultantDocuments.add(SolrContentItemConverter.solrDocument2solrContentItem(solrDocument,
                ldProgramName));
        }
        Map<String,Map<String,List<RelatedKeyword>>> relatedKeywords = new HashMap<String,Map<String,List<RelatedKeyword>>>();
        List<String> queryTerms = tokenizeEntities(queryTerm);

        for (String queryToken : queryTerms) {
            relatedKeywords.putAll(relatedKeywordSearchManager.getRelatedKeywordsFromAllSources(queryToken,
                ontologyURI).getRelatedKeywords());
        }
        return new FeaturedSearchResult(resultantDocuments, sortFacets(queryResponse.getFacetFields()),
                relatedKeywords);
    }

    @Override
    public SearchResult search(SolrParams solrQuery) throws SearchException {
        return search(solrQuery, null, null);
    }

    @Override
    public SearchResult search(SolrParams solrQuery, String ontologyURI, String ldProgramName) throws SearchException {
        QueryResponse queryResponse = solrSearch.search(solrQuery, ldProgramName);
        String queryTerm = SolrQueryUtil.extractQueryTermFromSolrQuery(solrQuery);
        return search(queryTerm, queryResponse, ontologyURI, ldProgramName);
    }

    @Override
    public List<String> getFieldNames() throws SearchException {
        return getFieldNames(null);
    }

    @Override
    public List<String> getFieldNames(String ldProgramName) throws SearchException {
        SolrServer solrServer = null;
        try {
            solrServer = SolrCoreManager.getInstance(bundleContext, managedSolrServer).getServer(
                ldProgramName);
        } catch (StoreException e) {
            String msg = String
                    .format("SolrSearchImpl.getFacetNames: Failed to obtain solr server for ldprogram: %s",
                        ldProgramName);
            log.error(msg, e);
            throw new SearchException(msg, e);
        }
        List<String> facetNames = null;
        try {
            facetNames = SolrQueryUtil.getFacetNames(solrServer);
        } catch (SolrServerException e) {
            String msg = "SolrSearchImpl.getFacetNames: Failed to execute solr query";
            log.error(msg, e);
            throw new SearchException(msg, e);
        } catch (IOException e) {
            throw new SearchException(e.getMessage(), e);
        }
        return facetNames;
    }

    @Override
    public List<String> tokenizeEntities(String queryTerm) {
        // obtain entities about query term through Enhancer
        ContentItem ci = null;
        boolean error = false;
        try {
            ci = new InMemoryContentItem(queryTerm.getBytes(Constants.DEFAULT_ENCODING), "text/plain");
            enhancementJobManager.enhanceContent(ci);
        } catch (UnsupportedEncodingException e) {
            log.error("Failed to get bytes of query term: {}", queryTerm, e);
            error = true;
        } catch (EnhancementException e) {
            log.error("Failed to get enmancements for the query term: {}", queryTerm, e);
            error = true;
        }

        List<String> tokenizedTerms = new ArrayList<String>();
        if (error || ci == null || ci.getMetadata() == null) {
            tokenizedTerms.add(queryTerm);
        } else {
            // traverse selected text assertions
            MGraph queryTermMetadata = ci.getMetadata();
            Iterator<Triple> textAnnotations = queryTermMetadata.filter(null,
                Properties.ENHANCER_SELECTED_TEXT, null);
            while (textAnnotations.hasNext()) {
                Resource r = textAnnotations.next().getObject();
                String selectedText = "";
                if (r instanceof Literal) {
                    selectedText = ((Literal) r).getLexicalForm();
                } else {
                    selectedText = r.toString();
                }

                tokenizedTerms.add(selectedText);
            }

            // get language of the query term
            String language = "en";
            Iterator<Triple> lanIt = queryTermMetadata.filter(null, Properties.DC_LANGUAGE, null);
            if (lanIt.hasNext()) {
                Resource r = lanIt.next().getObject();
                if (r instanceof Literal) {
                    language = ((Literal) r).getLexicalForm();
                } else {
                    language = r.toString();
                }
            }
            /*
             * If there is no stopword list for the language detected, it is highly possible that the default
             * language is detected is false. As English is the most common language, it is set as default.
             */
            if (!stopWords.containsKey(language)) {
                language = "en";
            }

            // eliminate entity query tokens from the original query term
            for (String queryToken : tokenizedTerms) {
                queryTerm = removeQueryToken(queryTerm, queryToken);
            }

            // find non-entity query tokens
            tokenizedTerms.addAll(getNonEntityQueryTerms(queryTerm, language));
        }
        return tokenizedTerms;
    }

    private Set<String> getNonEntityQueryTerms(String queryTerm, String language) {
        String currentWord = "";
        Set<String> queryTokens = new HashSet<String>();
        List<String> languageSpecificStopWords = stopWords.get(language);
        for (int i = 0; i < queryTerm.length(); i++) {
            if (SolrQueryUtil.queryDelimiters.contains(queryTerm.charAt(i))) {
                if (!currentWord.equals("")) {
                    if (languageSpecificStopWords != null) {
                        if (!languageSpecificStopWords.contains(currentWord.trim().toLowerCase())) {
                            queryTokens.add(currentWord);
                        }
                    } else {
                        queryTokens.add(currentWord);
                    }
                }
                currentWord = "";
                continue;
            }
            currentWord += queryTerm.charAt(i);
        }
        // check for the last word
        if (!currentWord.equals("")) {
            if (languageSpecificStopWords != null) {
                if (!languageSpecificStopWords.contains(currentWord.trim().toLowerCase())) {
                    queryTokens.add(currentWord);
                }
            } else {
                queryTokens.add(currentWord);
            }
        }
        return queryTokens;
    }

    private String removeQueryToken(String queryTerm, String queryToken) {
        String newTerm;
        int tokenStartIndex = queryTerm.indexOf(queryToken);
        if (tokenStartIndex != -1) {
            // find right delimeter
            int rightDelimeterIndex = tokenStartIndex + queryToken.length();
            for (; rightDelimeterIndex < queryTerm.length(); rightDelimeterIndex++) {
                if (SolrQueryUtil.queryDelimiters.contains(queryTerm.charAt(rightDelimeterIndex))) {
                    rightDelimeterIndex++;
                    break;
                }
            }
            newTerm = queryTerm.substring(0, tokenStartIndex);
            if (rightDelimeterIndex < queryTerm.length()) {
                newTerm += queryTerm.substring(rightDelimeterIndex);
            }
        } else {
            newTerm = queryTerm;
        }
        return newTerm;
    }
}