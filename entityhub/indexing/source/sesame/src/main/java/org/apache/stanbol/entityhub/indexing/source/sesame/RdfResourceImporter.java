package org.apache.stanbol.entityhub.indexing.source.sesame;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.apache.stanbol.entityhub.indexing.core.source.ResourceImporter;
import org.apache.stanbol.entityhub.indexing.core.source.ResourceState;
import org.openrdf.model.Resource;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdfResourceImporter implements ResourceImporter {

    Logger log = LoggerFactory.getLogger(RdfResourceImporter.class);
    
    private static Charset UTF8 = Charset.forName("UTF-8");
    
    private Repository repository;
    private Resource[] contexts;

    private String baseUri;

    public RdfResourceImporter(Repository repository, String baseUri, Resource...contexts){
        if(repository == null){
            throw new IllegalArgumentException("The parsed Repository MUST NOT be NULL");
        }
        if(!repository.isInitialized()){
            throw new IllegalStateException("The parsed Repository MUST BE initialised!");
        }
        this.repository = repository;
        if(baseUri == null){
            throw new IllegalArgumentException("The parsed base URI MUST NOT be NULL!");
        }
        this.baseUri = baseUri;
        this.contexts = contexts;
    }
    
    @Override
    public ResourceState importResource(InputStream is, String resourceName) throws IOException {
        log.info("> importing {}:", resourceName);
        RDFFormat rdfFormat = Rio.getParserFormatForFileName(resourceName);
        if(rdfFormat == null){
            log.info("  ... unable to detect RDF format for {}", resourceName);
            log.info("  ... resource '{}' will not be imported", resourceName);
            return ResourceState.IGNORED;
        } else {
            RepositoryConnection con = null;
            try {
                con = repository.getConnection();
                con.begin();
                con.add(new InputStreamReader(is, UTF8), baseUri, rdfFormat, contexts);
                con.commit();
                return ResourceState.LOADED;
            } catch (RDFParseException e) {
                log.error("  ... unable to parser RDF file " + resourceName
                    + " (format: "+rdfFormat+")", e);
                return ResourceState.ERROR;
            } catch (RepositoryException e) {
                throw new IllegalArgumentException("Repository Exception while "
                    + resourceName + "!",e);
            } finally {
                if(con != null){
                    try {
                        con.close();
                    } catch (RepositoryException e1) {/* ignore */}
                }
            }
        }
    }

}
