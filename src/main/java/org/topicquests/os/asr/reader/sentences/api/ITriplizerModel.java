/**
 * 
 */
package org.topicquests.os.asr.reader.sentences.api;

import org.topicquests.backside.kafka.consumer.api.IMessageConsumerListener;

/**
 * @author jackpark
 *
 */
public interface ITriplizerModel extends IMessageConsumerListener {

	
	void shutDown();
}
