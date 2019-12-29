/**
 * Copyright 2019, TopicQuests Foundation
 *  This source code is available under the terms of the Affero General Public License v3.
 *  Please see LICENSE.txt for full license terms, including the availability of proprietary exceptions.
 */
package org.topicquests.os.asr.reader.sentences;

import java.util.Map;

import org.topicquests.os.asr.reader.sentences.api.ITriplizerModel;
import org.topicquests.support.RootEnvironment;
import org.topicquests.support.config.Configurator;

/**
 * @author jackpark
 *
 */
public class TriplizerEnvironment extends RootEnvironment {
	private Map<String,Object>kafkaProps;
	private ITriplizerModel model;

	/**
	 */
	public TriplizerEnvironment() {
		super("triplizer-agent-props.xml", "logger.properties");
		kafkaProps = Configurator.getProperties("kafka-topics.xml");
		model = new TriplizerModel(this);

		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				shutDown();
			}
		});

	}

	public ITriplizerModel getModel() {
		return model;
	}
	
	public Map<String, Object> getKafkaTopicProperties() {
		return kafkaProps;
	}

	@Override
	public void shutDown() {
		System.out.println("TriplizerEnvironment.shutDown");
		model.shutDown();
	}

}
