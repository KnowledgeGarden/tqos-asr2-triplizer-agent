/**
 * Copyright 2019, TopicQuests Foundation
 *  This source code is available under the terms of the Affero General Public License v3.
 *  Please see LICENSE.txt for full license terms, including the availability of proprietary exceptions.
 */
package org.topicquests.os.asr.reader.sentences.patterns;

import java.util.*;

import org.topicquests.os.asr.reader.sentences.patterns.api.ISentenceTokenPatterns;
import org.topicquests.os.asr.reader.spacy.MuitiplePredicateSentenceInterpreter;
import org.topicquests.os.asr.reader.spacy.SinglePredicateSentenceInterpreter;
import org.topicquests.os.asr.reader.spacy.api.ISpacyConstants;
import org.topicquests.os.asr.reader.spacy.api.IParagraphObjectFields;
import org.topicquests.os.asr.reader.spacy.api.IInterpreterFields;
import org.topicquests.support.api.IEnvironment;

import net.minidev.json.JSONObject;

/**
 * @author jackpark
 *
 */
public class SentenceTokenPatternInterpreter {
	private IEnvironment environment;
	private SinglePredicateSentenceInterpreter singleInterp;
	private MuitiplePredicateSentenceInterpreter multiInterp;

	/**
	 * 
	 */
	public SentenceTokenPatternInterpreter(IEnvironment env) {
		environment = env;
		singleInterp = new SinglePredicateSentenceInterpreter(environment);
		multiInterp = new MuitiplePredicateSentenceInterpreter(environment);

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
	
	//////////////////////////
	// Sample sentence pattern - single predicate
	//	"DETdet NOUN ADPprep NOUN PUNCTpunct NOUN PUNCTpunct NOUN PUNCTpunct 
	//	CCONJcc NOUN PUNCTpunct NOUNappos PUNCTpunct 
	//	VERB 
	//	ADJamod NOUNnmod ADPcase 
	//	NOUN PUNCTpunct NUMdep PUNCTpunct CCONJcc ADVadvmod ADPcase NOUN 
	//	PUNCTpunct NOUNappos PUNCTpunct PUNCTpunct NUMappos PUNCTpunct PUNCTpunct"
	/////////////////////////
	/////////////////////////
	// Algorithm Ideas
	//	Given sentencePattern
	//	Given sentenceTokens list
	//	The pattern as a string can be used with REGEX-like search to determine
	//	simple patterns e.g. isA patterns
	//	The pattern broken into an array indexes with the sentenceTokens list
	//	ACT 1:
	//		Count the verbs to see if this is a single or multi-predicate sentence
	//	CASE 1: Single predicate sentence
	//	CASE 3: Multi-predicate sentence (vastly more complex)
	//////////////////////////

	public void interpretSentence(JSONObject sentenceObject, JSONObject paragraphObject) {
		String patterns = sentenceObject.getAsString(IParagraphObjectFields.SENTENCE_TOKEN_PATTERNS);
		List<JSONObject> sentenceTokens = (List<JSONObject>)sentenceObject.get(IParagraphObjectFields.SENTENCE_TOKEN_LIST);
		String [] patternArray = patterns.split(" ");
		//First Pass -- just get the predicate(s) and their actors, if any
		int verbCount = countVerbs(patternArray);
		environment.logDebug("SentenceTokenInterpret "+verbCount);
		if (verbCount == 1)
			singleInterp.processSinglePredicate(sentenceObject, sentenceTokens, patterns, patternArray);
		else
			multiInterp.processMultiPredicate(sentenceObject, sentenceTokens, patterns, patternArray);
		//Second Pass -- go looking for other patterns
		secondPass(sentenceObject, sentenceTokens, patterns, patternArray);
		diagnosticLog(sentenceObject);
	}
	
	void diagnosticLog(JSONObject sentenceObject) {
		String sent = sentenceObject.getAsString("text");
		List<List<JSONObject>> sentenceArray = 
				(List<List<JSONObject>>)sentenceObject.get(IParagraphObjectFields.SENTENCE_ARRAY_LIST_KEY);
		environment.logDebug("DONE\n"+sent+"\n"+sentenceArray);
		if (sentenceArray != null && !sentenceArray.isEmpty()) {
			List<List<String>>triples = new ArrayList<List<String>>();
			sentenceObject.put(IParagraphObjectFields.SENTENCE_TRIPLES, triples);
			List<String> triple;
			List<JSONObject>lj;
			JSONObject jo;
			Iterator<List<JSONObject>>itr = sentenceArray.iterator();
			while (itr.hasNext()) {
				lj = itr.next();
				if (!lj.isEmpty()) {
					environment.logDebug("DiagnosticLog "+lj);
					triple  = new ArrayList<String>();
					triple.add(lj.get(0).getAsString("text"));
					triple.add(lj.get(1).getAsString("text"));
					triple.add(lj.get(2).getAsString("text"));
					environment.logDebug("\n"+triple);
					triples.add(triple);
				}
			}
		}
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

	
	
	//////////////////////////
	// one kind of isA
	// DETdet NOUNA ADPprep NOUNB
	//  e.g. The pandemic of obesity
	//  Should validate "of"
	//	NOUNB isA NOUNA
	//////////////////////////
	// acronym
	// NOUNA PUNCT tag=-LRB- NOUNB tag=-RRB-
	//  NOUNA hasAcronym NOUNB
	//////////////////////////

	int whereVerb(String [] patterns) {
		int len = patterns.length;
		for (int i=0; i< len; i++) {
			if (patterns[i].startsWith("VERB"))
				return i;
		}
		return -1;
	}

	void secondPass(JSONObject sentenceObject,
					List<JSONObject> sentenceTokens,
					final String sentenceTokenPatterns, 
					final String [] patternArray) {
		int whereVerb = whereVerb(patternArray);
		List<JSONObject> beforeNouns = (List<JSONObject>)sentenceObject.get(IParagraphObjectFields.BEFORE_NOUN_TOKENS);
		List<JSONObject> afterNouns = (List<JSONObject>)sentenceObject.get(IParagraphObjectFields.BEFORE_NOUN_TOKENS);
		
		isA_1(sentenceObject, sentenceTokens, patternArray, whereVerb, beforeNouns, afterNouns);
		
		acronym_1(sentenceObject, sentenceTokens, patternArray, whereVerb);
	}

	// The 		pandemic	  of    obesity		,
	//"DETdet NOUNnsubjpass ADPprep NOUNpobj PUNCTpunct 
	// type 2 diabetes mellitus		(		T2DM		)		 and
	//NOUN 						PUNCTpunct NOUNappos PUNCTpunct CCONJcc 
	// nonalcoholic fatty liver disease			(		NAFLD		)
	//NOUN 										PUNCTpunct NOUNappos PUNCTpunct 
	// has frequently been associated with
	//VERB 
	// dietary intake of saturated fats		(  		1		 )  
	//NOUN 								PUNCTpunct NUMdep PUNCTpunct 
	// and 		specifically	with
	//CCONJcc   ADVadvmod 		ADPcase 
	// dietary palm oil  (			PO			) 			(		2			)			.
	//NOUN 				PUNCTpunct NOUNappos PUNCTpunct PUNCTpunct NUMappos PUNCTpunct PUNCTpunct

	/**
	 * Look for DET-NOUN-ADP-NOUN e.g. "the pandemic of obesity..."
	 * @param sentenceObject
	 * @param sentenceTokens
	 * @param patternArray
	 * @param whereVerb
	 * @param beforeNouns
	 * @param afterNouns
	 */
	void isA_1(JSONObject sentenceObject,
			   List<JSONObject> sentenceTokens,
			   final String [] patternArray, 
			   int whereVerb,
			   List<JSONObject> beforeNouns, 
			   List<JSONObject> afterNouns) {
		List<List<JSONObject>> sentenceArray = 
				(List<List<JSONObject>>)sentenceObject.get(IParagraphObjectFields.SENTENCE_ARRAY_LIST_KEY);
		int len = patternArray.length;
		String p, p1="", p2="", p3="";
		List<JSONObject> myArray;
		JSONObject jo, jc, jx;

		boolean before = true;
		int conjLen = 0;
		for (int i=0; i<len; i++) {
			if (i > whereVerb)
				before = false;
			p = patternArray[i];
			environment.logDebug("ISA-1-1 "+p+" "+p1+" "+p2+" "+p3);
			if (p.startsWith("DET")) {
				if ((i+1) < len) {
					p1 = patternArray[i+1];
					environment.logDebug("ISA-1-2 "+p+" "+p1+" "+p2+" "+p3);
					if (p1.startsWith("NOUN")) {
						if ((i+2) < len) {
							p2 = patternArray[i+2];
							environment.logDebug("ISA-1-3 "+p+" "+p1+" "+p2+" "+p3);
							if (p2.startsWith("ADP")) {
								if ((i+3) < len) {
									p3 = patternArray[i+3];
									environment.logDebug("ISA-1-4 "+p+" "+p1+" "+p2+" "+p3);
									if (p3.startsWith("NOUN")) {
										jo = sentenceTokens.get(i+2);
										if (jo.getAsString("text").equals("of")) {
											//pay dirt - this is a 
											if (sentenceArray == null) 
												sentenceArray = new ArrayList<List<JSONObject>>();
											jo = sentenceTokens.get(i+1); // get the isa Noun
											//deal with conjuncts if any
											if (before) {
												if (beforeNouns != null && !beforeNouns.isEmpty()) {
													jc = beforeNouns.get(0);
													jx = sentenceTokens.get(i+3);
													environment.logDebug("ISA-1-5\n"+jc+"\n"+jx);
													if (jc.getAsString("text").equals(jx.getAsString("text"))) {
														// it's all about the conjuncts.
														conjLen = beforeNouns.size();
														for (int j=0; j<conjLen; j++) {
															myArray = new ArrayList<JSONObject>();
															jc = beforeNouns.get(j);
															environment.logDebug("ISA-1-6 "+jc);
															myArray.add(jc);
															myArray.add(newVerb(IInterpreterFields.ISA_FIELD));
															myArray.add(jo);
															sentenceArray.add(myArray);
														}
														sentenceObject.put(IParagraphObjectFields.SENTENCE_ARRAY_LIST_KEY, sentenceArray);
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
	
	
	//NOUNA PUNCT tag=-LRB- NOUNB tag=-RRB-
	/**
	 * Look for a NOUN followed by a NOUN inside parens e.g. California (CA)
	 * @param sentenceObject
	 * @param sentenceTokens
	 * @param patternArray
	 * @param whereVerb
	 */
	void acronym_1(JSONObject sentenceObject,
				   List<JSONObject> sentenceTokens,
				   final String [] patternArray, 
				   int whereVerb) {
		List<List<JSONObject>> sentenceArray = 
				(List<List<JSONObject>>)sentenceObject.get(IParagraphObjectFields.SENTENCE_ARRAY_LIST_KEY);
		int len = patternArray.length;
		String p, p1="", p2="", p3="", tag;
		List<JSONObject> myArray;
		JSONObject jo, jc, jx;
		int conjLen = 0;
		for (int i=0; i<len; i++) {
			p = patternArray[i];
			environment.logDebug("ACRO-1-1 "+p+" "+p1+" "+p2+" "+p3);
			if (p.startsWith("NOUN")) {
				//This is a NOUN
				if ((i+1) < len) {
					p1 = patternArray[i+1];
					environment.logDebug("ACRO-1-2 "+p+" "+p1+" "+p2+" "+p3);
					if (p1.startsWith("PUNCT")) {
						jo = (JSONObject)sentenceTokens.get(i+1);
						tag = jo.getAsString("tag");
						environment.logDebug("ACRO-1-2a "+tag);
						if (tag != null && tag.equals("-LRB-")) {
							//It's followed by a right paren
							if ((i+2) < len) {
								p2 = patternArray[i+2];
								environment.logDebug("ACRO-1-3 "+p+" "+p1+" "+p2+" "+p3);
								if (p2.startsWith("NOUN")) {
									if ((i+3) < len) {
										p3 = patternArray[i+3];
										environment.logDebug("ACRO-1-4 "+p+" "+p1+" "+p2+" "+p3);
										if (p3.startsWith("PUNCT")) {
											jo = sentenceTokens.get(i+3);
											tag = jo.getAsString("tag");
											environment.logDebug("ACRO-1-4a "+tag);

											if (tag != null && tag.equals("-RRB-")) {
												//pay dirt - this is an acronym;
												if (sentenceArray == null) 
													sentenceArray = new ArrayList<List<JSONObject>>();
												jo = sentenceTokens.get(i); // get the Noun
												jc = sentenceTokens.get(i+2);
												myArray = new ArrayList<JSONObject>();
												myArray.add(jo);
												myArray.add(newVerb(IInterpreterFields.HAS_ACRONYM_FIELD));
												myArray.add(jc);
												sentenceArray.add(myArray);
												sentenceObject.put(IParagraphObjectFields.SENTENCE_ARRAY_LIST_KEY, sentenceArray);
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
	//////////////////////////
	// Synonym pattern
	//NOUN - PRON - VERB where PRON="that' VERB="has been called"
	//  has been called, is called, has been named, is named,
	//  could be others
	//////////////////////////

	
	JSONObject newVerb(String verbLabel) {
		JSONObject result = new JSONObject();
		result.put("text", verbLabel);
		result.put("pos", ISpacyConstants.VERB);
		return result;
	}

}
