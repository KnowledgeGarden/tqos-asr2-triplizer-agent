/**
 * 
 */
package org.topicquests.os.asr.reader.sentences;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.topicquests.os.asr.kafka.KafkaHandler;
import org.topicquests.os.asr.reader.sentences.api.ITriplizerModel;

import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

/**
 * @author jackpark
 *
 */
public class TriplizerModel implements ITriplizerModel {
	private TriplizerEnvironment environment;
	private KafkaHandler kafka;
	private List<String>docs;

	/**
	 * 
	 */
	public TriplizerModel(TriplizerEnvironment env) {
		environment = env;
		kafka = new KafkaHandler(environment, this);
		docs = new ArrayList<String>();
	}

	@Override
	public boolean acceptRecord(ConsumerRecord record) {
		String json = (String)record.value();
		environment.logDebug("BiomedSentenceAgent.acceptRecord "+json);
		boolean result = true; // default commit
		try {
			JSONParser p = new JSONParser(JSONParser.MODE_JSON_SIMPLE);
			JSONObject jo = (JSONObject)p.parse(json);
			JSONObject paraInfo = (JSONObject)jo.get("para_info");
			//environment.logDebug("BiomedSentenceAgent.acceptRecord "+paraInfo);
			JSONObject x = (JSONObject)paraInfo.get("hyp");
			//environment.logDebug("BiomedSentenceAgent.acceptRecord "+x);
			String docId = x.getAsString("id");
			//SANITY TEST
			if (docs.contains(docId)) 
				throw new RuntimeException("Spacy Not Advancing");
			else
				docs.add(docId);
			
			//TODO
		} catch (Exception e) {
			environment.logError(e.getMessage(), e);
			e.printStackTrace();
		}
		return result;	}

	@Override
	public void shutDown() {
		kafka.shutDown();
	}

}
