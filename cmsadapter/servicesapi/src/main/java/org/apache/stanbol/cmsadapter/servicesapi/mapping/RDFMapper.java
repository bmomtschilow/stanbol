package org.apache.stanbol.cmsadapter.servicesapi.mapping;

import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.stanbol.cmsadapter.servicesapi.helper.CMSAdapterVocabulary;

/**
 * Goal of this interface is to provide a uniform mechanism to store RDF data to JCR or CMIS repositories
 * based on CMS vocabulary annotations on top of the raw RDF. CMS vocabulary annotations are basically a few
 * {@link UriRef}s defined in {@link CMSAdapterVocabulary} indicating content repository information.
 * <p>
 * See {@link #storeRDFinRepository(Object, MGraph)} and
 * {@link #generateRDFFromRepository(Object, String, MGraph)} to learn behavior of this interface. Former
 * method updates the content repository according to annotated RDF and the latter one generates an annotated
 * RDF based on the content repository.
 * 
 * @author suat
 * 
 */
public interface RDFMapper {

    /**
     * This method stores the data passed within an {@link MGraph} to repository according
     * "CMS vocabulary annotations".
     * <p>
     * The only required annotation that this method handles is {@link CMSAdapterVocabulary#CMS_OBJECT}
     * assertions. This method should create each resource having this assertion as its rdf:type should be
     * created as a node/object in the repository.
     * <p>
     * The name of the CMS object to be created is first checked in
     * {@link CMSAdapterVocabulary#CMS_OBJECT_NAME} assertion. If the resource has not this assertion, the
     * name of the CMS object is set as the URI of the resource.
     * <p>
     * The location of the CMS object in the content repository is specified through the
     * {@link CMSAdapterVocabulary#CMS_OBJECT_PATH} assertion. If the resource has not this assertion the path
     * value is set with its name together with a preceding "/" character e.g "/"+name
     * <p>
     * Hierarchy between CMS object is set up by the {@link CMSAdapterVocabulary#CMS_OBJECT_PARENT_REF}
     * assertions. CMS objects are started to be created from the root object and based on this assertions
     * children are created.
     * <p>
     * 
     * @param session
     *            This is a session object which is used to interact with JCR or CMIS repositories
     * @param annotatedGraph
     *            This {@link MGraph} object is an enhanced version of raw RDF data with "CMS vocabulary"
     *            annotations
     * @throws RDFBridgeException
     */
    void storeRDFinRepository(Object session, MGraph annotatedGraph) throws RDFBridgeException;

    /**
     * This method generates an RDF from the part specified with a path of the content repository. It
     * transforms CMS objects into resources having {@link CMSAdapterVocabulary#CMS_OBJECT} rdf:type value. It
     * also transforms properties and types of the CMS object into the RDF. Furthermore, parent assertions are
     * added through the {@link CMSAdapterVocabulary#CMS_OBJECT_PARENT_REF}.
     * 
     * @param session
     *            This is a session object which is used to interact with JCR or CMIS repositories
     * @param rootPath
     *            Content repository path which is the root path indicating the root CMS object that will be
     *            transformed into RDF together with its children
     * @return annotated {@link MGraph}
     * @throws RDFBridgeException
     */
    MGraph generateRDFFromRepository(Object session, String rootPath) throws RDFBridgeException;

    /**
     * This method determines certain implementation of this interface is able to generate RDF from repository
     * or update the repository based on the given RDF.
     * 
     * @param connectionType
     *            connection type for which an {@link RDFMapper} is requested
     * @return whether this implementation can handle specified connection type
     */
    boolean canMap(String connectionType);
}
