package no.nav.sokos.lavendel.config

import kotlin.apply

import com.ibm.mq.constants.MQConstants
import com.ibm.mq.jakarta.jms.MQConnectionFactory
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import jakarta.jms.ConnectionFactory

private const val UTF_8_WITH_PUA = 1208
const val MQ_BATCH_SIZE = 200

object MQConfig {
    fun connectionFactory(properties: PropertiesConfig.MQProperties = PropertiesConfig.MQProperties()): ConnectionFactory =
        MQConnectionFactory().apply {
            transportType = WMQConstants.WMQ_CM_CLIENT
            queueManager = properties.mqQueueManagerName
            hostName = properties.hostname
            port = properties.port
            channel = properties.mqChannelName
            ccsid = UTF_8_WITH_PUA
            clientReconnectOptions = WMQConstants.WMQ_CLIENT_RECONNECT_Q_MGR
            setStringProperty(WMQConstants.WMQ_APPLICATIONNAME, PropertiesConfig.Configuration().naisAppName)
            setIntProperty(WMQConstants.JMS_IBM_ENCODING, MQConstants.MQENC_NATIVE)
            setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, UTF_8_WITH_PUA)
            setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, properties.userAuth)
        }
}
