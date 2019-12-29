/**
 * Copyright 2019, TopicQuests Foundation
 *  This source code is available under the terms of the Affero General Public License v3.
 *  Please see LICENSE.txt for full license terms, including the availability of proprietary exceptions.
 */
package org.topicquests.os.asr.reader.spacy;

import java.util.List;

import org.topicquests.os.asr.reader.spacy.api.IMuitiplePredicateSentenceInterpreter;
import org.topicquests.support.api.IEnvironment;

import net.minidev.json.JSONObject;

/**
 * @author jackpark
 *
 */
public class MuitiplePredicateSentenceInterpreter implements IMuitiplePredicateSentenceInterpreter {
	private IEnvironment environment;

	/**
	 * 
	 */
	public MuitiplePredicateSentenceInterpreter(IEnvironment env) {
		environment = env;
	}


	public void processMultiPredicate(JSONObject sentenceObject,
					List<JSONObject> sentenceTokens,
					final String pattern, final String [] patternArray) {
		List<List<JSONObject>> finalArray = null;
		
		int verbCount = countVerbs(patternArray);
		environment.logDebug("MultiPred "+verbCount+"\n"+sentenceObject.getAsString("text")+"\n"+sentenceTokens);
	}
	
	int countVerbs(String [] patterns) {
		int result = 0;
		int len = patterns.length;
		for (int i=0; i< len; i++) {
			if (patterns[i].startsWith("VERB"))
				result++;
		}
		return result;
	}


}
