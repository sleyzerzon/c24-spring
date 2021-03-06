/*
 * Copyright 2012 C24 Technologies.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *			http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.c24.io.spring.batch.processor;

import java.util.Collection;
import java.util.LinkedList;

import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Required;

import biz.c24.io.api.data.ComplexDataObject;
import biz.c24.io.api.data.ValidationEvent;
import biz.c24.io.api.data.ValidationException;
import biz.c24.io.api.data.ValidationListener;
import biz.c24.io.api.data.ValidationManager;
import biz.c24.io.api.presentation.JavaClassSink;
import biz.c24.io.api.transform.Transform;
import biz.c24.io.spring.batch.C24CompoundValidationException;
import biz.c24.io.spring.batch.reader.C24ValidationException;

/**
 * A Spring Batch ItemProcesor which invokes a C24 IO Transform to convert a CDO from one model to another.
 * Optionally transforms to a target-model compliant Java Bean
 * 
 * @author Andrew Elmore
 */
public class C24TransformItemProcessor implements ItemProcessor<ComplexDataObject, Object> {

	/**
	 * The C24 IO transform to use
	 */
	private Transform transformer;
	
	/**
	 * Whether or not to abort on the first failure
	 */
	private boolean failfast = true;
	
	private ThreadLocal<ValidationManager> validator = null;
	
	/**
	 * Optional JavaClassSink to use to convert CDOs to POJOs
	 */
	private JavaClassSink javaSink = null;

	/**
	 * Default constructor. Requires that the transformer is initialised separately.
	 */
	public C24TransformItemProcessor() {
		transformer = null;
	}
	
	/**
	 * Construct a C24TransformItemProcessor
	 * 
	 * @param transform The iO-generated transform to use
	 */
	public C24TransformItemProcessor(Transform transform) {
		setTransformer(transform);
	}
	
	/**
	 * Construct a C24TransformItemProcessor
	 * 
	 * @param transform The iO-generated transform to use
	 * @param validateOutput Whether or not we will validate the result of the transform
	 */
	public C24TransformItemProcessor(Transform transform, boolean validateOutput) {
		this(transform, validateOutput, true);
	}
	
	/**
	 * Construct a C24TransformItemProcessor
	 * 
	 * @param transform The iO-generated transform to use
	 * @param validateOutput Whether or not we will validate the result of the transform
	 * @param failfast Whether validation should abort on first failure or not
	 */
	public C24TransformItemProcessor(Transform transform, boolean validateOutput, boolean failfast) {
		setTransformer(transform);
		setValidation(validateOutput);
		setFailfast(failfast);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.springframework.batch.item.ItemProcessor#process(java.lang.Object)
	 */
	@Override
	public Object process(ComplexDataObject item) throws Exception {
		Object[][] transformedObj = transformer.transform(new Object[][]{{item}});
		
		ComplexDataObject result = (ComplexDataObject)transformedObj[0][0];
		
		if(validator != null) {
	        try {	
				ValidationManager mgr = validator.get();
				if(mgr == null) {
					mgr = new ValidationManager();
					validator.set(mgr);
				}
				if(failfast) {
					mgr.validateByException(result);
				} else {
					// Capture all failures
					final Collection<ValidationEvent> events = new LinkedList<ValidationEvent>();
					
					ValidationListener listener = new ValidationListener() {
			            public void validationPassed(ValidationEvent ve) {
			            }
	
			            public void validationFailed(ValidationEvent ve) {
			                events.add(ve);
			            }
			        };
			        
			        mgr.addValidationListener(listener);
			        
			        try {
				        if(!mgr.validateByEvents(result)) {
				        	if(events.size() == 1) {
				        		// Treat it as though we were validating by exception
				        		mgr.setEventBased(false);
				        		mgr.fireValidationEvent(events.iterator().next());
				        	} else {
				        		throw new C24CompoundValidationException(result, events);
				        	}
				        } 
			        } finally {
			        	mgr.removeValidationListener(listener);
			        }
				}
			} catch(ValidationException vEx) {
				throw new C24ValidationException("Failed to validate message: " + vEx.getLocalizedMessage(), result, vEx);
			}
		}
		
		if(javaSink != null) {
			return javaSink.convertObject(result);
		} else {		
			return result;
		}
	}

	/**
	 * Get the C24 IO transformer used by this ItemProcessor
	 * 
	 * @return The C24 IO transformer
	 */
	public Transform getTransformer() {
		return transformer;
	}

	/**
	 * Set the C24 IO transformer that this ItemProcessor will use
	 * 
	 * @param transformer The C24 IO transformer to use
	 */
	@Required
	public void setTransformer(Transform transformer) {
		this.transformer = transformer;
	}

	/**
	 * Whether or not this transformer validates the CDOs resulting from the transformation
	 * 
	 * @return True if if validates generated objects
	 */
	public boolean isValidating() {
		return validator != null;
	}

	/**
	 * Turn validation on or off
	 * 
	 * @param validate 
	 */
	public void setValidation(boolean validate) {
		validator = validate? new ThreadLocal<ValidationManager>() : null;
	}
	
	/**
	 * Releases any transient state left over from this transformation step
	 */
	@AfterStep
	public void cleanup() {
		// Release any validation managers we're holding; no guarantee the same thread pool will be used next time
		if(validator != null) {
			validator = new ThreadLocal<ValidationManager>();
		}
	}
	
	
	/**
	 * Returns the sink being used if any
	 * 
	 * @return The current JavaClassSink
	 */
	public Class<?> getTargetClass() {
		return javaSink != null? javaSink.getRootClass() : null;
	}

	/**
	 * Turns on/off returning POJOs or ComplexDataObjects
	 * 
	 * @param targetClass The Java Bean class to sink to, or CDO if null
	 */
	public void setTargetClass(Class<?> targetClass) {
		if(targetClass != null) {
			javaSink = new JavaClassSink();
			javaSink.setRootClass(targetClass);
		} else {
			javaSink = null;
		}
	}

	/**
	 * Do we abort on first failure or fully validate the object
	 * @return True iff this processor will abort on first failure
	 */
	public boolean isFailfast() {
		return failfast;
	}

	/**
	 * Controls whether this processor aborts on first failure or fully validates the object
	 * @param failFast
	 */
	public void setFailfast(boolean failfast) {
		this.failfast = failfast;
	}
	

}