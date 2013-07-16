/*****************************************************************************
 * Copyright (c) 2013 Obeo.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Obeo - initial API and implementation
 *****************************************************************************/
package fr.obeo.intent.specification.parser;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMLResourceFactoryImpl;
import org.eclipse.mylyn.docs.intent.collab.common.query.ModelingUnitQuery;
import org.eclipse.mylyn.docs.intent.collab.handlers.adapters.RepositoryAdapter;
import org.eclipse.mylyn.docs.intent.core.document.IntentSection;
import org.eclipse.mylyn.docs.intent.core.modelingunit.ExternalContentReference;
import org.eclipse.mylyn.docs.intent.core.modelingunit.ModelingUnit;
import org.eclipse.mylyn.docs.intent.core.modelingunit.ModelingUnitFactory;
import org.eclipse.mylyn.docs.intent.parser.external.parser.contribution.ExternalParserCompletionProposal;
import org.eclipse.mylyn.docs.intent.parser.external.parser.contribution.IntentExternalParserContribution;

import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;

import fr.obeo.intent.specification.Action;
import fr.obeo.intent.specification.AutomationLayer;
import fr.obeo.intent.specification.Behaviour;
import fr.obeo.intent.specification.Benefit;
import fr.obeo.intent.specification.Capability;
import fr.obeo.intent.specification.Context;
import fr.obeo.intent.specification.Feature;
import fr.obeo.intent.specification.NamedElement;
import fr.obeo.intent.specification.Role;
import fr.obeo.intent.specification.Scenario;
import fr.obeo.intent.specification.Specification;
import fr.obeo.intent.specification.SpecificationFactory;
import fr.obeo.intent.specification.Story;

/**
 * This parser is able to parse the specification syntax and create the
 * corresponding specification model.
 * 
 * @author
 */
public class SpecificationParser implements IntentExternalParserContribution {
	/**
	 * Comma.
	 */
	private static final String COMMA = ",";

	/**
	 * Closed bracket.
	 */
	private static final String CLOSE_BRACKET = "]";

	/**
	 * Opened bracket.
	 */
	private static final String OPEN_BRACKET = "[";

	/**
	 * Colon.
	 */
	private static final String COLON = ":";

	/**
	 * Specification factory.
	 */
	private SpecificationFactory specificationFactory = SpecificationFactory.eINSTANCE;

	/**
	 * Specification.
	 */
	private Specification specification;

	/**
	 * Automation layer.
	 */
	private AutomationLayer automationLayer;

	/**
	 * List of parsed elements.
	 */
	private List<ParsedElement> parsedElements = Lists.newArrayList();

	/**
	 * Specification path.
	 */
	private String SPECIFICATION_PATH = "/org.obeonetwork.dsl.uml2.doc.specification/intent.specification";

	/**
	 * Resource set.
	 */
	private ResourceSet resourceSet;

	/**
	 * Specification URI.
	 */
	private URI uri;

	/**
	 * Specification resource.
	 */
	private Resource resource;

	/**
	 * Specification parser keywords.
	 */
	private enum SpecificationKeyword {
		FEATURE("Feature"), STORY("Story"), AS("As"), I_WANT("I want"), SO_THAT(
				"So that"), SCENARIO("Scenario"), GIVEN("Given"), WHEN("When"), THEN(
				"Then");

		protected String value;

		private SpecificationKeyword(String value) {
			this.value = value;
		}
	}

	/**
	 * Default constructor.
	 */
	public SpecificationParser() {
		resourceSet = new ResourceSetImpl();
		uri = URI.createPlatformResourceURI(SPECIFICATION_PATH, true);
		resource = resourceSet.getResource(uri, true);
		init();
	}

	@Override
	public void parse(IntentSection intentSection, String descriptionUnitToParse) {
		// Get valid elements
		StringBuffer validElements = new StringBuffer();
		for (String element : Splitter.onPattern("\r?\n").trimResults()
				.omitEmptyStrings().split(descriptionUnitToParse)) {
			if (isValidElement(element)) {
				validElements.append(element + "\n");
			}
		}

		// Parse features
		parseFeatures(intentSection, validElements);

		// Parse stories
		parseStories(intentSection, validElements);

		// Parse scenarios
		parseScenarios(intentSection, validElements);
	}

	private void parseStories(IntentSection intentSection,
			StringBuffer validElements) {
		// Get valid stories
		StringBuffer validStories = new StringBuffer();
		for (String element : Splitter.onPattern("\r?\n").trimResults()
				.omitEmptyStrings().split(validElements)) {
			if (isStory(element)) {
				validStories.append(element + "\n");
			}
		}

		// "Story:"
		final String storyPattern = SpecificationKeyword.STORY.value + COLON;
		for (String description : Splitter.onPattern(storyPattern)
				.trimResults().omitEmptyStrings().split(validStories)) {
			Map<String, String> result = Splitter.onPattern("\r?\n")
					.trimResults().omitEmptyStrings()
					.withKeyValueSeparator(COLON)
					.split(storyPattern + description);
			String storyDescription = result
					.get(SpecificationKeyword.STORY.value);

			final String storyName = storyDescription.substring(0,
					storyDescription.indexOf(OPEN_BRACKET)).trim();

			NamedElement namedElement = getNamedElement(storyName, Story.class);
			Story story = null;
			if (namedElement == null) {
				story = specificationFactory.createStory();
				story.setName(storyName);
				specification.getStories().add((Story)story);
				parsedElements.add(new ParsedElement(intentSection, story));
			} else if (namedElement instanceof Story) {
				story = (Story)namedElement;
			} else {
				throw new UnsupportedOperationException();
			}

			String features = storyDescription.substring(
					storyDescription.indexOf(OPEN_BRACKET) + 1,
					storyDescription.indexOf(CLOSE_BRACKET));
			for (String featureName : Splitter.on(COMMA).trimResults()
					.omitEmptyStrings().split(features)) {
				namedElement = getNamedElement(featureName, Feature.class);
				Feature feature = null;
				if (namedElement == null) {
					feature = specificationFactory.createFeature();
					feature.setName(featureName);
					specification.getFeatures().add(feature);
					parsedElements
							.add(new ParsedElement(intentSection, feature));
				} else if (namedElement instanceof Feature) {
					feature = (Feature)namedElement;
				} else {
					throw new UnsupportedOperationException();
				}
				feature.getStories().add((Story)story);
			}

			String roleName = result.get(SpecificationKeyword.AS.value).trim();
			namedElement = getNamedElement(roleName, Role.class);
			Role role = null;
			if (namedElement == null) {
				role = specificationFactory.createRole();
				role.setName(roleName);
				specification.getRoles().add(role);
			} else if (namedElement instanceof Role) {
				role = (Role)namedElement;
			} else {
				throw new UnsupportedOperationException();
			}
			story.setAs(role);

			String capabilityName = result.get(
					SpecificationKeyword.I_WANT.value).trim();
			namedElement = getNamedElement(capabilityName, Capability.class);
			Capability capability = null;
			if (namedElement == null) {
				capability = specificationFactory.createCapability();
				capability.setName(capabilityName);
				specification.getCapabilities().add(capability);
			} else if (namedElement instanceof Capability) {
				capability = (Capability)namedElement;
			} else {
				throw new UnsupportedOperationException();
			}
			story.setIWant(capability);

			String benefitName = result.get(SpecificationKeyword.SO_THAT.value)
					.trim();
			namedElement = getNamedElement(benefitName, Benefit.class);
			Benefit benefit = null;
			if (namedElement == null) {
				benefit = specificationFactory.createBenefit();
				benefit.setName(benefitName);
				specification.getBenefits().add(benefit);
			} else if (namedElement instanceof Benefit) {
				benefit = (Benefit)namedElement;
			} else {
				throw new UnsupportedOperationException();
			}
			story.setSoThat(benefit);
		}
	}

	private void parseScenarios(IntentSection intentSection,
			StringBuffer validElements) {
		// Get valid scenarios
		StringBuffer validScenarios = new StringBuffer();
		for (String element : Splitter.onPattern("\r?\n").trimResults()
				.omitEmptyStrings().split(validElements)) {
			if (isScenario(element)) {
				validScenarios.append(element + "\n");
			}
		}

		// "Scenario:"
		final String scenarioPattern = SpecificationKeyword.SCENARIO.value
				+ COLON;
		for (String description : Splitter.onPattern(scenarioPattern)
				.trimResults().omitEmptyStrings().split(validScenarios)) {
			Iterable<String> results = Splitter.onPattern("\r?\n")
					.trimResults().omitEmptyStrings()
					.split(scenarioPattern + description);
			Scenario scenario = null;
			for (String result : results) {
				if (result.startsWith(SpecificationKeyword.SCENARIO.value)) {
					String scenarioDescription = Splitter
							.on(SpecificationKeyword.SCENARIO.value + COLON)
							.trimResults().omitEmptyStrings().split(result)
							.iterator().next();
					final String scenarioName = scenarioDescription.substring(
							0, scenarioDescription.indexOf(OPEN_BRACKET))
							.trim();

					NamedElement namedElement = getNamedElement(scenarioName,
							Scenario.class);
					if (namedElement == null) {
						scenario = specificationFactory.createScenario();
						scenario.setName(scenarioName);
						parsedElements.add(new ParsedElement(intentSection,
								scenario));
					} else if (namedElement instanceof Scenario) {
						scenario = (Scenario)namedElement;
					} else {
						throw new UnsupportedOperationException();
					}

					String stories = scenarioDescription.substring(
							scenarioDescription.indexOf(OPEN_BRACKET) + 1,
							scenarioDescription.indexOf(CLOSE_BRACKET));
					for (String storyName : Splitter.on(COMMA).trimResults()
							.omitEmptyStrings().split(stories)) {
						namedElement = getNamedElement(storyName, Story.class);
						Story story = null;
						if (namedElement == null) {
							story = specificationFactory.createStory();
							story.setName(storyName);
							specification.getStories().add(story);
							parsedElements.add(new ParsedElement(intentSection,
									story));
						} else if (namedElement instanceof Story) {
							story = (Story)namedElement;
						} else {
							throw new UnsupportedOperationException();
						}
						story.getScenarios().add(scenario);
					}
				} else if (result.startsWith(SpecificationKeyword.GIVEN.value)) {
					String contextName = Splitter
							.on(SpecificationKeyword.GIVEN.value + COLON)
							.trimResults().omitEmptyStrings().split(result)
							.iterator().next();
					NamedElement namedElement = getNamedElement(contextName,
							Context.class);
					Context context = null;
					if (namedElement == null) {
						context = specificationFactory.createContext();
						context.setName(contextName);
						automationLayer.getContext().add(context);
					} else if (namedElement instanceof Context) {
						context = (Context)namedElement;
					} else {
						throw new UnsupportedOperationException();
					}
					scenario.getGiven().add(context);
				} else if (result.startsWith(SpecificationKeyword.WHEN.value)) {
					String actionName = Splitter
							.on(SpecificationKeyword.WHEN.value + COLON)
							.trimResults().omitEmptyStrings().split(result)
							.iterator().next();
					NamedElement namedElement = getNamedElement(actionName,
							Action.class);
					Action action = null;
					if (namedElement == null) {
						action = specificationFactory.createAction();
						action.setName(actionName);
						automationLayer.getActions().add(action);
					} else if (namedElement instanceof Action) {
						action = (Action)namedElement;
					} else {
						throw new UnsupportedOperationException();
					}
					scenario.getWhen().add(action);
				} else if (result.startsWith(SpecificationKeyword.THEN.value)) {
					String behaviourName = Splitter
							.on(SpecificationKeyword.THEN.value + COLON)
							.trimResults().omitEmptyStrings().split(result)
							.iterator().next();
					NamedElement namedElement = getNamedElement(behaviourName,
							Behaviour.class);
					Behaviour behaviour = null;
					if (namedElement == null) {
						behaviour = specificationFactory.createBehaviour();
						behaviour.setName(behaviourName);
						automationLayer.getBehaviours().add(behaviour);
					} else if (namedElement instanceof Behaviour) {
						behaviour = (Behaviour)namedElement;
					} else {
						throw new UnsupportedOperationException();
					}
					scenario.getThen().add(behaviour);
				}
			}
		}
	}

	/**
	 * Text to parse : "Feature: MyFeature1
	 * [ReferencedFeature1,ReferencedFeature2] Feature: MyFeature2
	 * [ReferencedFeature2,ReferencedFeature3]"
	 * 
	 * @param intentSection
	 * @param validElements
	 * @return
	 */
	private void parseFeatures(IntentSection intentSection,
			StringBuffer validElements) {
		// Get valid features
		StringBuffer validFeatures = new StringBuffer();
		for (String element : Splitter.onPattern("\r?\n").trimResults()
				.omitEmptyStrings().split(validElements)) {
			if (isFeature(element)) {
				validFeatures.append(element + "\n");
			}
		}

		// "Feature:"
		final String featurePattern = SpecificationKeyword.FEATURE.value
				+ COLON;

		for (String description : Splitter.onPattern(featurePattern)
				.trimResults().omitEmptyStrings().split(validFeatures)) {
			Map<String, String> result = Splitter.onPattern("\r?\n")
					.trimResults().omitEmptyStrings()
					.withKeyValueSeparator(COLON)
					.split(featurePattern + description);
			String featureDescription = result
					.get(SpecificationKeyword.FEATURE.value);

			final String featureName = featureDescription.substring(0,
					featureDescription.indexOf(OPEN_BRACKET)).trim();

			NamedElement namedElement = getNamedElement(featureName,
					Feature.class);
			Feature feature = null;
			if (namedElement == null) {
				feature = specificationFactory.createFeature();
				feature.setName(featureName);
				specification.getFeatures().add((Feature)feature);
				parsedElements.add(new ParsedElement(intentSection, feature));
			} else if (namedElement instanceof Feature) {
				feature = (Feature)namedElement;
			} else {
				throw new UnsupportedOperationException();
			}

			String features = featureDescription.substring(
					featureDescription.indexOf(OPEN_BRACKET) + 1,
					featureDescription.indexOf(CLOSE_BRACKET));
			for (String refFeatureName : Splitter.on(COMMA).trimResults()
					.omitEmptyStrings().split(features)) {
				namedElement = getNamedElement(refFeatureName, Feature.class);
				Feature refFeature = null;
				if (namedElement == null) {
					refFeature = specificationFactory.createFeature();
					refFeature.setName(refFeatureName);
					specification.getFeatures().add(refFeature);
					parsedElements.add(new ParsedElement(intentSection,
							refFeature));
				} else if (namedElement instanceof Feature) {
					refFeature = (Feature)namedElement;
				} else {
					throw new UnsupportedOperationException();
				}
				refFeature.getRefFeatures().add((Feature)feature);
			}
		}
	}

	/**
	 * Check if an element is a feature.
	 * 
	 * @param element
	 *            Element
	 * @return True if the element is a feature
	 */
	private boolean isFeature(String element) {
		return element.startsWith(SpecificationKeyword.FEATURE.value);
	}

	/**
	 * Check if an element is a story.
	 * 
	 * @param element
	 *            Element
	 * @return True if the element is a story
	 */
	private boolean isStory(String element) {
		return element.startsWith(SpecificationKeyword.STORY.value)
				|| element.startsWith(SpecificationKeyword.AS.value)
				|| element.startsWith(SpecificationKeyword.I_WANT.value)
				|| element.startsWith(SpecificationKeyword.SO_THAT.value);
	}

	/**
	 * Check if an element is a scenario.
	 * 
	 * @param element
	 *            Element
	 * @return True if the element is a scenario
	 */
	private boolean isScenario(String element) {
		return element.startsWith(SpecificationKeyword.SCENARIO.value)
				|| element.startsWith(SpecificationKeyword.GIVEN.value)
				|| element.startsWith(SpecificationKeyword.WHEN.value)
				|| element.startsWith(SpecificationKeyword.THEN.value);
	}

	/**
	 * Check if an element is a specification element (feature, story,
	 * scenario...).
	 * 
	 * @param element
	 *            Element
	 * @return True if the element is a specification element
	 */
	private boolean isValidElement(String element) {
		for (SpecificationKeyword keyword : SpecificationKeyword.class
				.getEnumConstants()) {
			if (element.startsWith(keyword.value + COLON)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Get a named element defined in the specification.
	 * 
	 * @param elementName
	 *            Name of the element to search
	 * @param type
	 *            Type of the element
	 * @return Named element or null if does not exist in the current
	 *         specification
	 */
	@SuppressWarnings("rawtypes")
	private NamedElement getNamedElement(final String elementName,
			final Class type) {
		UnmodifiableIterator<EObject> it = Iterators.filter(
				specification.eAllContents(), new Predicate<EObject>() {
					public boolean apply(EObject eObject) {
						if (eObject != null
								&& eObject instanceof NamedElement
								&& type.getSimpleName().equals(
										eObject.eClass().getName())) {
							return elementName.equals(((NamedElement)eObject)
									.getName());
						}
						return false;
					}
				});
		if (it.hasNext()) {
			return (NamedElement)it.next();
		}
		return null;
	}

	@Override
	public void init() {
		parsedElements.clear();
		specification = specificationFactory.createSpecification();
		automationLayer = specificationFactory.createAutomationLayer();
		specification.setAutomationLayer(automationLayer);
	}

	@Override
	public void parsePostOperations(RepositoryAdapter repositoryAdapter) {
		for (ParsedElement parsedElement : parsedElements) {
			// Create a reference in the section if not exists
			ModelingUnitQuery query = new ModelingUnitQuery(repositoryAdapter);
			Collection<ExternalContentReference> externalContentReferences = query
					.getAllExternalContentReferences();
			URI uri = SpecificationUtils.getTestURI(parsedElement
					.getNamedElement());
			if (!referenceExists(externalContentReferences, uri)) {
				createReference(parsedElement.getIntentSection(), uri);
			}
		}

		// Serialize intent.specification
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("specification", new XMLResourceFactoryImpl());
		try {
			resource.delete(null);
		} catch (IOException e) {
			SpecificationParserActivator.log(Status.ERROR, "The resource "
					+ uri.devicePath() + "cannot be deleted", e);
		}
		resource = resourceSet.createResource(uri);
		resource.getContents().add(specification);

		try {
			resource.save(null);
		} catch (IOException e) {
			SpecificationParserActivator.log(Status.ERROR, "The resource "
					+ uri.devicePath() + "cannot be saved", e);
		}
	}

	/**
	 * Create a reference.
	 * 
	 * @param intentSection
	 *            Intent section
	 * @param uri
	 *            URI of the file to reference
	 */
	private void createReference(IntentSection intentSection, URI uri) {
		ModelingUnit modelingUnit = ModelingUnitFactory.eINSTANCE
				.createModelingUnit();
		ExternalContentReference referenceInstruction = ModelingUnitFactory.eINSTANCE
				.createExternalContentReference();
		referenceInstruction.setUri(uri);
		referenceInstruction.setMarkedAsMerged(true);

		modelingUnit.getInstructions().add(referenceInstruction);

		intentSection.getIntentContent().add(
				intentSection.getIntentContent().size(), modelingUnit);
	}

	/**
	 * Check if a reference exists in the given reference list.
	 * 
	 * @param externalContentReferences
	 *            Existing references
	 * @param uri
	 *            URI to check
	 * @return True if reference exists in the given list otherwise false
	 */
	private boolean referenceExists(
			Collection<ExternalContentReference> externalContentReferences,
			URI uri) {
		if (externalContentReferences.size() == 0)
			return false;
		for (ExternalContentReference externalContentReference : externalContentReferences) {
			if (uri.equals(externalContentReference.getUri()))
				return true;
		}
		return false;
	}

	@Override
	public List<ExternalParserCompletionProposal> getCompletionVariablesProposals(
			Iterable<String> currentSentences) {
		// Get last written sentence
		Iterator<String> iterator = currentSentences.iterator();
		String currentSentence = null;
		while (iterator.hasNext()) {
			currentSentence = (String)iterator.next();
		}

		List<ExternalParserCompletionProposal> variables = Lists.newArrayList();
		if ((currentSentence.contains(SpecificationKeyword.FEATURE.value) || currentSentence
				.contains(SpecificationKeyword.STORY.value))
				&& currentSentence.contains(OPEN_BRACKET)) {
			// Feature: Name[Features] or Story: Name [Features]
			variables.addAll(getAllFeatures());
		} else if ((currentSentence
				.contains(SpecificationKeyword.SCENARIO.value))
				&& currentSentence.contains(OPEN_BRACKET)) {
			// Scenario: Name[Stories]
			variables.addAll(getAllStories());
		} else if (currentSentence.contains(SpecificationKeyword.AS.value)) {
			// As:
			variables.addAll(getAllRoles());
		} else if (currentSentence.contains(SpecificationKeyword.I_WANT.value)) {
			// I want:
			variables.addAll(getAllCapabilities());
		} else if (currentSentence.contains(SpecificationKeyword.SO_THAT.value)) {
			// So that:
			variables.addAll(getAllBenefits());
		} else if (currentSentence.contains(SpecificationKeyword.GIVEN.value)) {
			// Given:
			variables.addAll(getAllContexts());
		} else if (currentSentence.contains(SpecificationKeyword.WHEN.value)) {
			// When:
			variables.addAll(getAllActions());
		} else if (currentSentence.contains(SpecificationKeyword.THEN.value)) {
			// Then:
			variables.addAll(getAllBehaviours());
		}
		return variables;
	}

	/**
	 * Get all roles defined in the specification.
	 * 
	 * @return All roles defined in the specification or an empty list
	 */
	private List<ExternalParserCompletionProposal> getAllRoles() {
		URI uri = URI.createPlatformResourceURI(SPECIFICATION_PATH, true);
		Resource resource = resourceSet.getResource(uri, true);
		Specification specification = (Specification)resource.getContents()
				.get(0);
		List<ExternalParserCompletionProposal> results = Lists.newArrayList();
		for (Role role : specification.getRoles()) {
			results.add(new ExternalParserCompletionProposal(role.getName(),
					Role.class.getSimpleName(), null,
					SpecificationParserActivator.getDefault().getImage(
							"icon/specification/Role.gif")));
		}

		return results;
	}

	/**
	 * Get all benefits defined in the specification.
	 * 
	 * @return All benefits defined in the specification or en empty list
	 */
	private List<ExternalParserCompletionProposal> getAllBenefits() {
		Specification specification = (Specification)resource.getContents()
				.get(0);
		List<ExternalParserCompletionProposal> results = Lists.newArrayList();
		for (Benefit benefit : specification.getBenefits()) {
			results.add(new ExternalParserCompletionProposal(benefit.getName(),
					Benefit.class.getSimpleName(), null,
					SpecificationParserActivator.getDefault().getImage(
							"icon/specification/Benefit.gif")));
		}

		return results;
	}

	/**
	 * Get all capabilities defined in the specification.
	 * 
	 * @return All capabilities defined in the specification or an empty list
	 */
	private List<ExternalParserCompletionProposal> getAllCapabilities() {
		Specification specification = (Specification)resource.getContents()
				.get(0);
		List<ExternalParserCompletionProposal> results = Lists.newArrayList();
		for (Capability capability : specification.getCapabilities()) {
			results.add(new ExternalParserCompletionProposal(capability
					.getName(), Capability.class.getSimpleName(), null,
					SpecificationParserActivator.getDefault().getImage(
							"icon/specification/Capability.gif")));
		}

		return results;
	}

	/**
	 * Get all contexts defined in the specification.
	 * 
	 * @return All contexts defined in the specification or an empty list
	 */
	private List<ExternalParserCompletionProposal> getAllContexts() {
		Specification specification = (Specification)resource.getContents()
				.get(0);
		List<ExternalParserCompletionProposal> results = Lists.newArrayList();
		for (Context context : specification.getAutomationLayer().getContext()) {
			results.add(new ExternalParserCompletionProposal(context.getName(),
					Context.class.getSimpleName(), null,
					SpecificationParserActivator.getDefault().getImage(
							"icon/specification/Context.gif")));
		}

		return results;
	}

	/**
	 * Get all behaviours defined in the specification.
	 * 
	 * @return All behaviours defined in the specification or an empty list
	 */
	private List<ExternalParserCompletionProposal> getAllBehaviours() {
		Specification specification = (Specification)resource.getContents()
				.get(0);
		List<ExternalParserCompletionProposal> results = Lists.newArrayList();
		for (Behaviour behaviour : specification.getAutomationLayer()
				.getBehaviours()) {
			results.add(new ExternalParserCompletionProposal(behaviour
					.getName(), Behaviour.class.getSimpleName(), null,
					SpecificationParserActivator.getDefault().getImage(
							"icon/specification/Behaviour.gif")));
		}

		return results;
	}

	/**
	 * Get all actions defined in the specification.
	 * 
	 * @return All actions defined in the specification or an empty list
	 */
	private List<ExternalParserCompletionProposal> getAllActions() {
		Resource resource = resourceSet.getResource(uri, true);
		Specification specification = (Specification)resource.getContents()
				.get(0);
		List<ExternalParserCompletionProposal> results = Lists.newArrayList();
		for (Action action : specification.getAutomationLayer().getActions()) {
			results.add(new ExternalParserCompletionProposal(action.getName(),
					Action.class.getSimpleName(), null,
					SpecificationParserActivator.getDefault().getImage(
							"icon/specification/Action.gif")));
		}

		return results;
	}

	/**
	 * Get all features defined in the specification.
	 * 
	 * @return All features defined in the specification or an empty list
	 */
	private List<ExternalParserCompletionProposal> getAllFeatures() {
		Specification specification = (Specification)resource.getContents()
				.get(0);
		List<ExternalParserCompletionProposal> results = Lists.newArrayList();
		for (Feature feature : specification.getFeatures()) {
			results.add(new ExternalParserCompletionProposal(feature.getName(),
					Feature.class.getSimpleName(), null,
					SpecificationParserActivator.getDefault().getImage(
							"icon/specification/Feature.gif")));
		}

		return results;
	}

	/**
	 * Get all stories defined in the specification.
	 * 
	 * @return All stories defined in the specification or an empty list
	 */
	private List<ExternalParserCompletionProposal> getAllStories() {
		Specification specification = (Specification)resource.getContents()
				.get(0);
		List<ExternalParserCompletionProposal> results = Lists.newArrayList();
		for (Story story : specification.getStories()) {
			results.add(new ExternalParserCompletionProposal(story.getName(),
					Story.class.getSimpleName(), null,
					SpecificationParserActivator.getDefault().getImage(
							"icon/specification/Story.gif")));
		}

		return results;
	}

	@Override
	public List<ExternalParserCompletionProposal> getCompletionTemplatesProposals(
			Iterable<String> currentSentences) {
		List<ExternalParserCompletionProposal> templates = Lists.newArrayList();
		Iterator<String> iterator = currentSentences.iterator();
		String currentSentence = null;
		while (iterator.hasNext()) {
			currentSentence = (String)iterator.next();
		}

		if (currentSentence
				.endsWith(SpecificationKeyword.FEATURE.value + COLON)) {
			// Feature:
			templates.add(new ExternalParserCompletionProposal("Feature",
					"Declare a new feature", " ${Name} [${ParentFeature}]",
					SpecificationParserActivator.getDefault().getImage(
							"icon/specification/Feature.gif")));
		} else if (currentSentence.endsWith(SpecificationKeyword.STORY.value
				+ COLON)) {
			// Story:
			templates
					.add(new ExternalParserCompletionProposal(
							"Story",
							"Declare a new story",
							" ${Name} [${ReferencingFeature}]\n\tAs: ${Role}\n\tI want: ${Capability}\n\tSo that: ${Benefit}",
							SpecificationParserActivator.getDefault().getImage(
									"icon/specification/Story.gif")));
		} else {
			// Add all the possible templates
			templates.add(new ExternalParserCompletionProposal("Feature",
					"Declare a new feature",
					"Feature: ${Name} [${ParentFeature}]",
					SpecificationParserActivator.getDefault().getImage(
							"icon/specification/Feature.gif")));
			templates
					.add(new ExternalParserCompletionProposal(
							"Story",
							"Declare a new story",
							"Story: ${Name} [${ReferencingFeature}]\n\tAs: ${Role}\n\tI want: ${Capability}\n\tSo that: ${Benefit}",
							SpecificationParserActivator.getDefault().getImage(
									"icon/specification/Story.gif")));
			templates
					.add(new ExternalParserCompletionProposal(
							"Scenario",
							"Declare a new scenario",
							"Scenario: ${Name} [${ParentStory}]\n\tGiven: ${Context}\n\tWhen: ${Action}\n\tThen: ${Behaviour}",
							SpecificationParserActivator.getDefault().getImage(
									"icon/specification/Scenario.gif")));
		}
		return templates;
	}
}