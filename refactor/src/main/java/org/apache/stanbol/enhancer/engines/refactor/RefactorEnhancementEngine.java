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
package org.apache.stanbol.enhancer.engines.refactor;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.access.TcProvider;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.owl.transformation.OWLAPIToClerezzaConverter;
import org.apache.stanbol.enhancer.engines.refactor.dereferencer.Dereferencer;
import org.apache.stanbol.enhancer.engines.refactor.dereferencer.DereferencerImpl;
import org.apache.stanbol.enhancer.servicesapi.ContentItem;
import org.apache.stanbol.enhancer.servicesapi.EngineException;
import org.apache.stanbol.enhancer.servicesapi.EnhancementEngine;
import org.apache.stanbol.enhancer.servicesapi.ServiceProperties;
import org.apache.stanbol.enhancer.servicesapi.helper.AbstractEnhancementEngine;
import org.apache.stanbol.entityhub.core.utils.OsgiUtils;
import org.apache.stanbol.entityhub.model.clerezza.RdfRepresentation;
import org.apache.stanbol.entityhub.model.clerezza.RdfValueFactory;
import org.apache.stanbol.entityhub.servicesapi.model.Entity;
import org.apache.stanbol.entityhub.servicesapi.model.Representation;
import org.apache.stanbol.entityhub.servicesapi.site.ReferencedSiteManager;
import org.apache.stanbol.ontologymanager.ontonet.api.ONManager;
import org.apache.stanbol.ontologymanager.ontonet.api.collector.DuplicateIDException;
import org.apache.stanbol.ontologymanager.ontonet.api.collector.UnmodifiableOntologyCollectorException;
import org.apache.stanbol.ontologymanager.ontonet.api.io.OntologyContentInputSource;
import org.apache.stanbol.ontologymanager.ontonet.api.io.OntologyInputSource;
import org.apache.stanbol.ontologymanager.ontonet.api.io.RootOntologyIRISource;
import org.apache.stanbol.ontologymanager.ontonet.api.io.RootOntologySource;
import org.apache.stanbol.ontologymanager.ontonet.api.ontology.OntologyProvider;
import org.apache.stanbol.ontologymanager.ontonet.api.scope.OntologyScope;
import org.apache.stanbol.ontologymanager.ontonet.api.scope.OntologySpace;
import org.apache.stanbol.ontologymanager.ontonet.api.scope.ScopeRegistry;
import org.apache.stanbol.ontologymanager.ontonet.api.session.Session;
import org.apache.stanbol.ontologymanager.ontonet.api.session.SessionLimitException;
import org.apache.stanbol.ontologymanager.ontonet.api.session.SessionManager;
import org.apache.stanbol.rules.base.api.NoSuchRecipeException;
import org.apache.stanbol.rules.base.api.Recipe;
import org.apache.stanbol.rules.base.api.Rule;
import org.apache.stanbol.rules.base.api.RuleStore;
import org.apache.stanbol.rules.base.api.util.RuleList;
import org.apache.stanbol.rules.refactor.api.Refactorer;
import org.apache.stanbol.rules.refactor.api.RefactoringException;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologySetProvider;
import org.semanticweb.owlapi.util.OWLOntologyMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * This an engine to post-process the enhancements. Its main goal is to refactor the RDF produced by the
 * enhancement applying some vocabulary related to a specific task.
 * 
 * To do that, exploit a Refactor recipe and an ontology scope of OntoNet.
 * 
 * The first implementation is targeted to SEO use case. * It retrieves data by dereferencing the entities, *
 * includes the DBpedia ontology * refactor the data using the google rich snippets vocabulary.
 * 
 * @author andrea.nuzzolese, alberto.musetti
 * 
 */
@Component(configurationFactory = true, policy = ConfigurationPolicy.REQUIRE, specVersion = "1.1", metatype = true, immediate = true, inherit = true)
@Service
@Properties(value = {@Property(name = EnhancementEngine.PROPERTY_NAME, value = "seo_refactoring")

})
public class RefactorEnhancementEngine extends AbstractEnhancementEngine<RuntimeException,RuntimeException>
        implements EnhancementEngine, ServiceProperties {

    /**
     * A special input source that allows to bind a physical IRI with an ontology parsed from an input stream.
     * Due to its unconventional nature it is kept private.
     * 
     * @author alexdma
     * 
     */
    private class OntologyContentSourceWithPhysicalIRI extends OntologyContentInputSource {

        public OntologyContentSourceWithPhysicalIRI(InputStream content, IRI physicalIri) throws OWLOntologyCreationException {
            this(content, physicalIri, OWLManager.createOWLOntologyManager());
        }

        public OntologyContentSourceWithPhysicalIRI(InputStream content,
                                                    IRI physicalIri,
                                                    OWLOntologyManager manager) throws OWLOntologyCreationException {
            super(content, manager);
            bindPhysicalIri(physicalIri);
        }

    }

    @Property(boolValue = true)
    public static final String APPEND_OTHER_ENHANCEMENT_GRAPHS = RefactorEnhancementEngineConf.APPEND_OTHER_ENHANCEMENT_GRAPHS;

    @Property(value = "google_rich_snippet_rules")
    public static final String RECIPE_ID = RefactorEnhancementEngineConf.RECIPE_ID;

    @Property(value = "")
    public static final String RECIPE_LOCATION = RefactorEnhancementEngineConf.RECIPE_LOCATION;

    @Property(value = "seo")
    public static final String SCOPE = RefactorEnhancementEngineConf.SCOPE;

    @Property(cardinality = 1000, value = {"http://ontologydesignpatterns.org/ont/iks/kres/dbpedia_demo.owl"})
    public static final String SCOPE_CORE_ONTOLOGY = RefactorEnhancementEngineConf.SCOPE_CORE_ONTOLOGY;

    @Property(boolValue = true)
    public static final String USE_ENTITY_HUB = RefactorEnhancementEngineConf.USE_ENTITY_HUB;

    private ComponentContext context;

    @Reference
    Dereferencer dereferencer;

    private RefactorEnhancementEngineConf engineConfiguration;

    private final Object lock = new Object();

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference
    ONManager onManager;

    @Reference
    OntologyProvider<TcProvider> ontologyProvider;

    private ComponentInstance refactorEngineComponentInstance;

    @Reference
    Refactorer refactorer;

    @Reference
    ReferencedSiteManager referencedSiteManager;

    @Reference
    RuleStore ruleStore;

    private OntologyScope scope;

    @Reference
    SessionManager sessionManager;

    /**
     * Activating the component
     * 
     * @param context
     */
    @SuppressWarnings("unchecked")
    @Activate
    protected void activate(final ComponentContext context) throws ConfigurationException {
        log.info("in " + RefactorEnhancementEngine.class + " activate with context " + context);
        if (context == null) {
            throw new IllegalStateException("No valid" + ComponentContext.class + " parsed in activate!");
        }
        super.activate(context);
        this.context = context;

        Map<String,Object> config = new HashMap<String,Object>();
        Dictionary<String,Object> properties = (Dictionary<String,Object>) context.getProperties();
        // copy the properties to a map
        for (Enumeration<String> e = properties.keys(); e.hasMoreElements();) {
            String key = e.nextElement();
            config.put(key, properties.get(key));
            log.debug("Configuration property: " + key + " :- " + properties.get(key));
        }

        // Initialize engine-specific features.
        engineConfiguration = new DefaultRefactorEnhancementEngineConf(properties);
        initEngine(engineConfiguration);

        log.debug(RefactorEnhancementEngine.class + " activated.");
    }

    @Override
    public int canEnhance(ContentItem ci) throws EngineException {
        /*
         * Being a post-processing engine, the Refactor can enhance only content items that are previously
         * enhanced by other enhancement engines.
         */
        return ci.getMetadata() == null ? CANNOT_ENHANCE : ENHANCE_SYNCHRONOUS;
    }

    @Override
    public void computeEnhancements(ContentItem ci) throws EngineException {

        // Prepare the OntoNet environment. First we create the OntoNet session in which run the whole
        final Session session;
        try {
            session = sessionManager.createSession();
        } catch (SessionLimitException e1) {
            throw new EngineException(
                    "OntoNet session quota reached. The Refactor Engine requires its own new session to execute.");
        }
        log.debug("Refactor enhancement job will run in session '{}'.", session.getID());

        // Retrieve and filter the metadata graph for entities recognized by the engines.
        final MGraph mGraph = ci.getMetadata();
        // FIXME the Stanbol Enhancer vocabulary should be retrieved from somewhere in the enhancer API.
        final UriRef ENHANCER_ENTITY_REFERENCE = new UriRef(
                "http://fise.iks-project.eu/ontology/entity-reference");
        Iterator<Triple> tripleIt = mGraph.filter(null, ENHANCER_ENTITY_REFERENCE, null);
        while (tripleIt.hasNext()) {
            // Get the entity URI
            Resource obj = tripleIt.next().getObject();
            if (!(obj instanceof UriRef)) {
                log.warn("Invalid UriRef for entity reference {}. Skipping.", obj);
                continue;
            }
            final String entityReference = ((UriRef) obj).getUnicodeString();
            log.debug("Trying to resolve entity {}", entityReference);
            // We fetch the entity in the OntologyInputSource object
            try {
                /*
                 * The RDF graph of an entity is fetched via the EntityHub. The getEntityOntology is a method
                 * the do the job of asking the entity to the EntityHub and wrap the RDF graph into an
                 * OWLOntology.
                 */
                OntologyInputSource<OWLOntology,?> ontologySource;

                if (engineConfiguration.isEntityHubUsed()) {
                    ontologySource = new RootOntologySource(getEntityOntology(entityReference));
                } else {
                    ontologySource = new OntologyContentSourceWithPhysicalIRI(
                            dereferencer.resolve(entityReference), IRI.create(entityReference));
                }

                if (session != null && ontologySource != null) session.addOntology(ontologySource);
                log.debug("Added " + entityReference + " to the session space of scope "
                          + engineConfiguration.getScope(), this);

            } catch (UnmodifiableOntologyCollectorException e) {
                log.error("Cannot populate locked session '{}'. Aborting.", session.getID());
                break;
            } catch (OWLOntologyCreationException e) {
                log.error("Failed to obtain ontology for entity " + entityReference + ". Skipping.", e);
                continue;
            } catch (FileNotFoundException e) {
                log.error("Failed to obtain ontology for entity " + entityReference + ". Skipping.", e);
                continue;
            }

        }

        // Now merge the RDF from the TBox - the ontologies - and the ABox - the RDF data fetched
        final OWLOntologyManager omgr = OWLManager.createOWLOntologyManager();

        OWLOntologySetProvider provider = new OWLOntologySetProvider() {

            @Override
            public Set<OWLOntology> getOntologies() {
                Set<OWLOntology> ontologies = new HashSet<OWLOntology>();
                ontologies.addAll(session.getManagedOntologies(OWLOntology.class, true));
                /*
                 * We add to the set the graph containing the metadata generated by previous enhancement
                 * engines. It is important becaus we want to menage during the refactoring also some
                 * information fron that graph. As the graph is provided as a Clerezza MGraph, we first need
                 * to convert it to an OWLAPI OWLOntology. There is no chance that the mGraph could be null as
                 * it was previously controlled by the JobManager through the canEnhance method and the
                 * computeEnhancement is always called iff the former returns true.
                 */
                OWLOntology fiseMetadataOntology = OWLAPIToClerezzaConverter
                        .clerezzaGraphToOWLOntology(mGraph);
                ontologies.add(fiseMetadataOntology);
                return ontologies;
            }
        };

        /*
         * We merge all the ontologies from the session into a single ontology that will be used for the
         * refactoring.
         * 
         * TODO the refactorer should have methods to accommodate OntologyCollector instead.
         */
        OWLOntologyMerger merger = new OWLOntologyMerger(provider);
        OWLOntology ontology;
        try {
            ontology = merger.createMergedOntology(omgr,
                IRI.create("http://fise.iks-project.eu/dulcifier/integrity-check"));

            log.debug("Refactoring recipe IRI is : " + engineConfiguration.getRecipeId());

            /*
             * We pass the ontology and the recipe IRI to the Refactor that returns the refactored graph
             * expressed by using the given vocabulary.
             */
            try {
                /*
                 * To perform the refactoring of the ontology to a given vocabulary we use the Stanbol
                 * Refactor.
                 */
                Recipe recipe = ruleStore.getRecipe(IRI.create(engineConfiguration.getRecipeId()));

                log.debug("Recipe {} contains {} rules.", recipe, recipe.getkReSRuleList().size());
                log.debug("The ontology to be refactor is {}", ontology);

                ontology = refactorer.ontologyRefactoring(ontology,
                    IRI.create(engineConfiguration.getRecipeId()));

            } catch (RefactoringException e) {
                log.error("The refactoring engine failed the execution.", e);
            } catch (NoSuchRecipeException e) {
                log.error("The recipe with ID " + engineConfiguration.getRecipeId() + " does not exists", e);
            }

            log.debug("Merged ontologies in " + ontology);

            /*
             * The new generated ontology is converted to Clarezza format and than added os substitued to the
             * old mGraph.
             */
            if (engineConfiguration.isInGraphAppendMode()) {
                log.debug("Metadata of the content will replace old ones.", this);
            } else {
                mGraph.clear();
                log.debug("Content metadata will be appended to the existing ones.", this);
            }
            mGraph.addAll(OWLAPIToClerezzaConverter.owlOntologyToClerezzaTriples(ontology));

            /*
             * The session needs to be destroyed, as it is no more useful.
             * 
             * clear contents before destroying (FIXME only do this until this is implemented in the
             * destroySession() method).
             */
            for (IRI iri : session.listManagedOntologies()) {
                try {
                    String key = ontologyProvider.getKey(iri);
                    ontologyProvider.getStore().deleteTripleCollection(new UriRef(key));
                } catch (Exception ex) {
                    log.error("Failed to delete triple collection " + iri, ex);
                    continue;
                }
            }
            sessionManager.destroySession(session.getID());

        } catch (OWLOntologyCreationException e) {
            throw new EngineException("Cannot create the ontology for the refactoring", e);
        }
    }

    @SuppressWarnings("unchecked")
    protected void createRefactorEngineComponent(ComponentFactory factory) {
        // both create*** methods sync on the searcherAndDereferencerLock to avoid
        // multiple component instances because of concurrent calls
        synchronized (this.lock) {
            if (refactorEngineComponentInstance == null) {
                this.refactorEngineComponentInstance = factory.newInstance(OsgiUtils.copyConfig(context
                        .getProperties()));
            }
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        /*
         * Deactivating the dulcifier. The procedure require: 1) get all the rules from the recipe 2) remove
         * the recipe. 3) remove the single rule. 4) tear down the scope ontologySpace and the scope itself.
         */

        try {
            /*
             * step 1: get all the rule
             */
            log.debug("Removing recipe " + engineConfiguration.getRecipeId() + " from RuleStore.", this);
            RuleList recipeRuleList = ruleStore.getRecipe(IRI.create(engineConfiguration.getRecipeId()))
                    .getkReSRuleList();

            /*
             * step 2: remove the recipe
             */
            if (ruleStore.removeRecipe(IRI.create(engineConfiguration.getRecipeId()))) {
                log.debug("The recipe " + engineConfiguration.getRecipeId() + " has been removed correctly");
            } else {
                log.error("The recipe " + engineConfiguration.getRecipeId() + " can not be removed");
            }

            /*
             * step 3: remove the rules
             */
            for (Rule rule : recipeRuleList) {
                if (ruleStore.removeRule(rule)) {
                    log.debug("The rule " + rule.getRuleName() + " has been removed correctly");
                } else {
                    log.error("The rule " + rule.getRuleName() + " can not be removed");
                }
            }

            /*
             * step 4:
             */
            scope.getCoreSpace().tearDown();
            scope.tearDown();
            onManager.getScopeRegistry().deregisterScope(scope);
        } catch (NoSuchRecipeException ex) {
            log.error("The recipe " + engineConfiguration.getRecipeId() + " doesn't exist", ex);
        }

        log.info("Deactivated Refactor Enhancement Engine");

    }

    /**
     * Fetch the OWLOntology containing the graph associated to an entity from Linked Data. It uses the Entity
     * Hub for accessing LOD and fetching entities.
     * 
     * @param entityURI
     *            {@link String}
     * @return the {@link OWLOntology} of the entity
     */
    private OWLOntology getEntityOntology(String entityURI) {
        log.debug("Asking entity: " + entityURI);
        OWLOntology fetchedOntology = null;
        // Ask the entityhub the fetch the entity.
        Entity entitySign = referencedSiteManager.getEntity(entityURI);
        MGraph entityMGraph = null; // The entity graph to be wrapped into an OWLOntology.

        if (entitySign != null) {
            Representation entityRepresentation = entitySign.getRepresentation();
            RdfRepresentation entityRdfRepresentation = RdfValueFactory.getInstance().toRdfRepresentation(
                entityRepresentation);
            TripleCollection tripleCollection = entityRdfRepresentation.getRdfGraph();
            entityMGraph = new SimpleMGraph();
            entityMGraph.addAll(tripleCollection);
        }

        if (entityMGraph != null) fetchedOntology = OWLAPIToClerezzaConverter
                .clerezzaGraphToOWLOntology(entityMGraph);

        return fetchedOntology;
    }

    @Override
    public Map<String,Object> getServiceProperties() {
        return Collections.unmodifiableMap(Collections.singletonMap(
            ServiceProperties.ENHANCEMENT_ENGINE_ORDERING,
            (Object) ServiceProperties.ORDERING_POST_PROCESSING));
    }

    /**
     * Method for adding ontologies to the scope core ontology.
     * <ol>
     * <li>Get all the ontologies from the property.</li>
     * <li>Create a base scope.</li>
     * <li>Retrieve the ontology space from the scope.</li>
     * <li>Add the ontologies to the scope via ontology space.</li>
     * </ol>
     */
    private void initEngine(RefactorEnhancementEngineConf engineConfiguration) {

        // IRI dulcifierScopeIRI = IRI.create((String) context.getProperties().get(SCOPE));
        String scopeId = engineConfiguration.getScope();

        // Create or get the scope with the configured ID
        ScopeRegistry scopeRegistry = onManager.getScopeRegistry();
        try {
            scope = onManager.getOntologyScopeFactory().createOntologyScope(scopeId);
            // No need to deactivate a newly created scope.
        } catch (DuplicateIDException e) {
            scope = scopeRegistry.getScope(scopeId);
            scopeRegistry.setScopeActive(scopeId, false);
        }
        // All resolvable ontologies stated in the configuration are loaded into the core space.
        OntologySpace ontologySpace = scope.getCoreSpace();
        ontologySpace.tearDown();
        String[] coreScopeOntologySet = engineConfiguration.getScopeCoreOntologies();
        List<String> success = new ArrayList<String>(), failed = new ArrayList<String>();
        try {
            log.info("Will now load requested ontology into the core space of scope '{}'.", scopeId);
            OWLOntologyManager sharedManager = OWLManager.createOWLOntologyManager();
            IRI physicalIRI = null;
            for (int o = 0; o < coreScopeOntologySet.length; o++) {
                String url = coreScopeOntologySet[o];
                try {
                    physicalIRI = IRI.create(url);
                } catch (Exception e) {
                    failed.add(url);
                }
                try {
                    // TODO replace with a Clerezza equivalent
                    ontologySpace.addOntology(new RootOntologyIRISource(physicalIRI, sharedManager));
                    success.add(url);
                } catch (OWLOntologyCreationException e) {
                    log.error("Failed to load ontology from physical location " + physicalIRI
                              + " Continuing with next...", e);
                    failed.add(url);
                }
            }
        } catch (UnmodifiableOntologyCollectorException ex) {
            log.error("Ontology space {} was found locked for modification. Cannot populate.", ontologySpace);
        }
        for (String s : success)
            log.info(" >> {} : SUCCESS", s);
        for (String s : failed)
            log.info(" >> {} : FAILED", s);
        ontologySpace.setUp();
        if (!scopeRegistry.containsScope(scopeId)) scopeRegistry.registerScope(scope);
        scopeRegistry.setScopeActive(scopeId, true);

        /*
         * The first thing to do is to create a recipe in the rule store that can be used by the engine to
         * refactor the enhancement graphs.
         */
        String recipeId = engineConfiguration.getRecipeId();
        ruleStore.addRecipe(IRI.create(recipeId), null);
        log.debug("Initialised blank recipe with ID {}", recipeId);

        /*
         * The set of rule to put in the recipe can be provided by the user. A default set of rules is
         * provided in /META-INF/default/seo_rules.sem. Use the property engine.refactor in the felix console
         * to pass to the engine your set of rules.
         */
        String recipeLocation = engineConfiguration.getRecipeLocation();

        InputStream recipeStream = null;
        String recipeString = null;

        if (recipeLocation != null && !recipeLocation.isEmpty()) {
            Dereferencer dereferencer = new DereferencerImpl();
            try {
                recipeStream = dereferencer.resolve(recipeLocation);
                log.debug("Loaded recipe from external source {}", recipeLocation);
            } catch (FileNotFoundException e) {
                log.error("Recipe Stream is null.", e);
            }
        } else {
            // TODO remove this part (or manage it better in the @Activate method).
            String loc = "/META-INF/default/seo_rules.sem";
            recipeStream = getClass().getResourceAsStream(loc);
            log.debug("Loaded default recipe in {}.", loc);
        }

        if (recipeStream != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(recipeStream));
            recipeString = "";
            String line = null;
            try {
                while ((line = reader.readLine()) != null)
                    recipeString += line;
            } catch (IOException e) {
                log.error("Failed to load Refactor Engine recipe from stream. Aborting read. ", e);
                recipeString = null;
            }
        }
        log.debug("Recipe content follows :\n{}", recipeString);
        if (recipeString != null) try {
            ruleStore.addRuleToRecipe(recipeId, recipeString);
            log.debug("Added rules to recipe {}", recipeId);
        } catch (NoSuchRecipeException e) {
            log.error("Failed to add rules to recipe {}. Recipe was not found.", recipeId);
        }
    }

}
