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
package biz.c24.io.spring.batch.writer.source;

import java.io.Writer;

import org.springframework.batch.core.StepExecution;

/**
 * Abstraction for implementations of WriterSources. Implemented by classes which can provide Writers
 * to a C24ItemWriter for it to write sunk CDOs to.
 * 
 * @author Andrew Elmore
 */
public interface WriterSource {
	/**
	 * Initialise the WriterSource
	 * 
	 * @param stepExecution
	 */
	public abstract void initialise(StepExecution stepExecution);

	/**
	 * Close the source, releasing any held resources
	 */
	public abstract void close();

	/**
	 * Return a Writer that callers can use to persist daa.
	 * The Writer returned can change between calls.
	 * 
	 * @return A Writer
	 */
	public abstract Writer getWriter();
}
