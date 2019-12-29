/**
 * Copyright 2019, TopicQuests Foundation
 *  This source code is available under the terms of the Affero General Public License v3.
 *  Please see LICENSE.txt for full license terms, including the availability of proprietary exceptions.
 */
package org.topicquests.os.asr.reader.spacy;

import java.util.ArrayList;
import java.util.List;

import org.topicquests.os.asr.reader.spacy.api.ISinglePredicateSentenceInterpreter;
import org.topicquests.os.asr.reader.spacy.api.ISpacyConstants;
import org.topicquests.os.asr.reader.spacy.api.IParagraphObjectFields;
import org.topicquests.support.api.IEnvironment;

import kafka.utils.Json;
import net.minidev.json.JSONObject;

/**
 * @author jackpark
 * <p>
 * This algorithm deals with a sentence with a single predicate
 * e.g.
 * 	A causes B which is the simple pattern NPN
 * But which can entail conjunctive subjects and/or objects, which
 * means that there are actually multiple triple patterns from one sentence.</p>
 * <p>
 * 
 * </p>
 */
public class SinglePredicateSentenceInterpreter implements ISinglePredicateSentenceInterpreter{
	private IEnvironment environment;
	private SpacyUtil util;

	/**
	 * 
	 */
	public SinglePredicateSentenceInterpreter(IEnvironment env) {
		environment = env;
		util = new SpacyUtil(environment);
	}

	
	/**
	 * BUG!!!!!
	 	2019-12-24 12:42:29 DEBUG LoggingPlatform:46 - DiagnosticLog [
	 	{"parent":203,"pos":"DET","start":188,"lemma":"the","text":"The","tag":"DT","sent":188,"dep":"det"}, 
	 	{"pos":"NOUN","start":192,"width":"2","end":203,"text":"inhibitory components"}, 
	 	{"pos":"VERB","start":214,"width":"3","end":228,"text":"were examined by"}, 
	 	{"pos":"NOUN","start":231,"width":"2","end":241,"text":"different approaches"}, 
	 	{"parent":219,"pos":"PUNCT","start":251,"text":".","tag":".","sent":188,"dep":"punct"}]
		2019-12-24 12:42:29 DEBUG LoggingPlatform:46 - 
		[The, inhibitory components, were examined by]
		SHOULD READ
		[inhibitory components, were examined by, different approaches]
		SHOULD NOT allow a DET to start a triple
	 */

	/**
	 * 
	 * @param sentenceObject
	 * @param sentenceTokens
	 * @param pattern
	 * @param patternArray
	 */
	public void processSinglePredicate(JSONObject sentenceObject,
									   List<JSONObject> sentenceTokens,
									   final String pattern, 
									   final String [] patternArray) {
		List<List<JSONObject>> finalArray = null;
		int whereVerb = whereVerb(patternArray);
		if (whereVerb == -1)
			throw new RuntimeException("VerbNotFound: "+pattern);
		environment.logDebug("SinglePred\n"+sentenceObject.getAsString("text"));
		//isolate nouns before and after the verb(hrase)
		List<JSONObject> [] ba = beforeAfterNouns(sentenceTokens, patternArray, whereVerb);
		List<JSONObject> bNouns = ba[0];
		List<JSONObject> aNouns = ba[1];

		// this counts "," and conjunctions
		// Technically speaking, nouns begin just before a "," or conjunction, and continue after
		int N = bNouns.size();
		int M = aNouns.size();
		int numArrays = 0;
		if (N == 0 && M > 0)
			numArrays = M;
		else if (M == 0 && N > 0)
			numArrays = N;
		if (N > 0 && M > 0)
			numArrays = (N) * (M);
		//BOTH OF THE FOLLOWING ARE ILLEGAL
		// We are here because there is a Verb which should have some kind of
		// noun before and after it
		else if (N > 0)
			numArrays = N;
		else
			numArrays = M;
		environment.logDebug("SinglePred-1 "+N+" "+M+" "+numArrays+"\n"+sentenceTokens);
		finalArray = new ArrayList<List<JSONObject>>();
		if (numArrays == 1) {
			finalArray.add(sentenceTokens);
		} else {
			for (int i=0;i<numArrays;i++) {
				finalArray.add(new ArrayList<JSONObject>());
			}
			processSentence(finalArray, 
					sentenceTokens, 
					N, 
					M, 
					patternArray, 
					whereVerb, 
					sentenceObject,
					bNouns,
					aNouns);
		}
		sentenceObject.put(IParagraphObjectFields.SENTENCE_ARRAY_LIST_KEY, finalArray);
	}
	
	/**
	 * Here because before and/or after noun counts > 1
	 * @param finalArray
	 * @param sentenceTokens
	 * @param beforeConjuncts
	 * @param afterConjuncts
	 * @param patternArray
	 * @param whereVerb
	 * @param sentenceObject 
	 * @param bNouns - nouns before verb
	 * @param aNouns - nouns after verb
	 */
	void processSentence(List<List<JSONObject>> finalArray,
						 List<JSONObject> sentenceTokens,
						 int beforeConjuncts, 
						 int afterConjuncts,
						 String [] patternArray, 
						 int whereVerb,
						 JSONObject sentenceObject,
						 List<JSONObject> bNouns,
						 List<JSONObject> aNouns) {
		environment.logDebug("ProcSent "+beforeConjuncts+" "+afterConjuncts+"\n"+sentenceTokens);
		environment.logDebug("ProcSent-1\n"+bNouns+"\n"+aNouns);
		// retain these for later processing
		sentenceObject.put(IParagraphObjectFields.BEFORE_NOUN_TOKENS, bNouns);
		sentenceObject.put(IParagraphObjectFields.AFTER_NOUN_TOKENS, aNouns);
		if (afterConjuncts == 1)
			oneAfter(finalArray, sentenceTokens, bNouns, aNouns, whereVerb);
		else if (beforeConjuncts == 1)
			oneBefore(finalArray, sentenceTokens, bNouns, aNouns, whereVerb);
		else
			both(finalArray, sentenceTokens, bNouns, aNouns, whereVerb);
	}
	
	void oneAfter(List<List<JSONObject>> finalArray,
				  List<JSONObject> sentenceTokens, 
				  List<JSONObject> bNouns,
				  List<JSONObject> aNouns, 
				  int whereVerb) {
		int finalSize = finalArray.size();
		environment.logDebug("oneAfter "+whereVerb+"\n"+bNouns+"\n"+aNouns);
		if (bNouns == null || bNouns.isEmpty() || aNouns == null || aNouns.isEmpty()) {
			environment.logError("oneAfter bad", null);
			return;
		}
		//In this case, finalSize should == bNouns size
		JSONObject afterToken = aNouns.get(0);
		List<JSONObject>l;
		for (int i=0; i< finalSize; i++) {
			l = finalArray.get(i);
			l.add(bNouns.get(i));
			l.add(sentenceTokens.get(whereVerb));
			l.add(afterToken);
		}
	}
	
	void oneBefore(List<List<JSONObject>> finalArray,
				   List<JSONObject> sentenceTokens, 
				   List<JSONObject> bNouns,
				   List<JSONObject> aNouns,
				   int whereVerb) {
		int finalSize = finalArray.size();
		environment.logDebug("oneBefore "+whereVerb+"\n"+bNouns+"\n"+aNouns);
		//In this case, finalSize should == aNouns size
		JSONObject beforeToken = bNouns.get(0);
		List<JSONObject>l;
		for (int i=0; i< finalSize; i++) {
			l = finalArray.get(i);
			l.add(beforeToken);
			l.add(sentenceTokens.get(whereVerb));
			l.add(aNouns.get(i));
		}
		
	}
	
	/**
	 * There are N nouns before the predicate and M nouns after it
	 * @param finalArray
	 * @param sentenceTokens
	 * @param bNouns
	 * @param aNouns
	 * @param whereVerb
	 */
	void both(List<List<JSONObject>> finalArray,
			  List<JSONObject> sentenceTokens, 
			  List<JSONObject> bNouns,
			  List<JSONObject> aNouns, 
			  int whereVerb) {
		int finalSize = finalArray.size();
		environment.logDebug("both "+whereVerb+"\n"+bNouns+"\n"+aNouns);
		// in this case, it's more complex: finalSize = aNouns size * bNouns size
		int bSize = bNouns.size();
		int aSize = aNouns.size();
		int n = bSize;
		int m = aSize;
		environment.logDebug("both-1 "+n+" "+m);
		List<JSONObject>l;
		JSONObject b, a;
		int counter = 0;
		for (int i=0; i<n; i++) {
			// for each of the before nouns
			b = bNouns.get(i);
			// use b "n" times
			for (int j=0; j<m; j++) {
				// for each of the after nouns
				// fill a column that includes the b,verb,a 
				environment.logDebug("both-2 "+i+" "+j);
				a = aNouns.get(j);
				l = finalArray.get(i+counter+j);
				l.add(b);
				l.add(sentenceTokens.get(whereVerb));
				l.add(a);
			}
			counter++;
		}
	}
	
	////////////////////////////////
	// Possible confounding pattern
	// NOUNnsubj VERB DETcc_preconj NOUNpobj CCONJcc NOUN PUNCTpunct
	// where the verb is followed by a DET
	////////////////////////////////

	
	int whereVerb(String [] patterns) {
		int len = patterns.length;
		for (int i=0; i< len; i++) {
			if (patterns[i].startsWith("VERB"))
				return i;
		}
		return -1;
	}
	

	/**
	 * <p>Looking for conjunctive nouns before and after the verb</p>
	 * <p>Some nouns must be filtered based on certain patterns</p>
	 * @param sentenceTokens
	 * @param patternArray
	 * @param whereVerb
	 * @return
	 */
	List<JSONObject> [] beforeAfterNouns(List<JSONObject> sentenceTokens,
			 							 String [] patternArray, 
			 							 int whereVerb) {
		environment.logDebug("SinglePred.beforeAfterNouns\n"+sentenceTokens);
		List<JSONObject> [] result = new ArrayList[2];
		List<JSONObject> lb = new ArrayList<JSONObject>();
		result[0] = lb;
		List<JSONObject> la = new ArrayList<JSONObject>();
		result[1] = la;
		int len = patternArray.length;
		String p, pp;
		JSONObject jo, jx;
		boolean before = true;
		boolean isFirst = true;
		JSONObject thisTok, prior = null;
		String  ptext, thistext;
		boolean contains = false;
		boolean leadingCCONJ = false;
		boolean trailingComma = false;
		//boolean isNoun = false;
		int whereFirst = 0;
		for (int i=0; i<len; i++) {
			//for every pattern in the pattern array
			contains = false;
			if (i == whereVerb) {
				before = false;
				prior = null;
			}
			p = patternArray[i];
			environment.logDebug("SinglePred.BAN "+p+" "+isFirst+" "+leadingCCONJ);
			if (p.startsWith(ISpacyConstants.CCONJ))
				leadingCCONJ = true;
			else if (p.startsWith(ISpacyConstants.NOUN)) {
				// First is the beginning noun of a sentence
				// even if there are no conjuncts after it
				jo = sentenceTokens.get(i);
				environment.logDebug("SinglePred.BAN-0\n"+jo);
				if (validateNoun(i, jo, sentenceTokens)) { 
					if (isFirst) {
						whereFirst = i;
						lb = new ArrayList<JSONObject>();
						la = new ArrayList<JSONObject>();
						lb.add(sentenceTokens.get(i));
						isFirst = false;
					} else if (leadingCCONJ) {
						environment.logDebug("SinglePred.BAN-1 "+leadingCCONJ+" "+before);
	
						if (before)
							lb.add(sentenceTokens.get(i));
						else
							la.add(sentenceTokens.get(i));
						environment.logDebug("SinglePred.BAN-1A\n"+lb+"\n"+la);
					} else {
						// NOUN, not first, no leading CCONJ
						// look for trailing "," or trailing cconj
						if ((i+1) < len) {
							pp = patternArray[i+1];
							environment.logDebug("SinglePred.BAN-2 "+pp+"\n"+jo);
							if (pp.startsWith(ISpacyConstants.PUNCT)) {
								jx = sentenceTokens.get(i+1);
								environment.logDebug("SinglePred.BAN-2a\n"+jx);
								if (jx.getAsString("text").equals(",")) {
									environment.logDebug("SinglePred.BAN-3 "+before);
									//trailing comma
									// use this array location
									if (before)
										lb.add(sentenceTokens.get(i));
									else
										la.add(sentenceTokens.get(i));
									leadingCCONJ = false;
									environment.logDebug("SinglePred.BAN-4\n"+lb+"\n"+la);
								} else {
									environment.logDebug("SinglePred.BAN-4a "+before);
									
									if (before)
										lb.add(sentenceTokens.get(i));
									else
										la.add(sentenceTokens.get(i));									
								}
							} else if (pp.startsWith(ISpacyConstants.CCONJ)) {
								environment.logDebug("SinglePred.BAN-5 "+before);
								if (before)
									lb.add(sentenceTokens.get(i));
								else
									la.add(sentenceTokens.get(i));
								leadingCCONJ = false;
								environment.logDebug("SinglePred.BAN-6\n"+lb+"\n"+la);
							}
						}
					}
				}
			}
		}
		result[0] = lb;
		result[1] = la;
		environment.logDebug("SinglePred.BAN+\n"+lb+"\n"+la);
		return result;
	}
	
	////////////////////////////////
	// NOTE:
	//	Modified validateNoun to toss a bitch if token is not a NOUN.
	//	There was a bug where somehow a DET "The" got through and took
	//	the place of subject
	//	That would suggest that the tokenArray which thought this was to be a noun
	//	is out of synch with the sentence itself
	// If it fails, search above for SinglePred.BAN and SinglePred.BAN-0
	////////////////////////////////
	/**
	 * <p>Will return {@code false} if <br>
	 * a) the noun is followed by a ")" - this noun is an acronym<br/>
	 * b) the noun is followed by a "of"</p>
	 * <p>In which case, this noun is not qualified to exist in a noun phrase</p>
	 * @param where
	 * @param token
	 * @param masterTokens
	 * @return
	 */
	boolean validateNoun(int where, JSONObject token, List<JSONObject> masterTokens) {
		//sanity
		String p = token.getAsString("pos");
		if (!p.equalsIgnoreCase("NOUN"))
			throw new RuntimeException("BAD NOUN "+token);
		boolean result = true;
		int len = masterTokens.size();
		JSONObject jo;
		boolean x;
		if ((where+1) < len) {
			jo = masterTokens.get(where+1);
			// is it followed by ")"?
			x = (jo.getAsString("pos").equals(ISpacyConstants.PUNCT) && jo.getAsString("tag").equals("-RRB-"));
			environment.logDebug("SinglePred.validateNoun "+x+"\n"+token+"\n"+jo);
			if (!x) {
				// is it followed by "of"?
				x = (jo.getAsString("pos").equals(ISpacyConstants.ADP) && jo.getAsString("text").equals("of"));
				environment.logDebug("SinglePred.validateNoun-1 "+x+"\n"+token+"\n"+jo);
				if (x) {
					//prove it's not following a DET
					if ((where-1)>=0) {
						jo = masterTokens.get(where-1);
						environment.logDebug("SinglePred.validateNoun-2\n"+token+"\n"+jo);
						result &= !(jo.getAsString("pos").equals(ISpacyConstants.DET));

					}
				}
				
				/*if (result) {
					result &= !(jo.getAsString("pos").equals(ISpacyConstants.CCONJ));
		
				}*/
			}
		}
		return result;
	}
}
