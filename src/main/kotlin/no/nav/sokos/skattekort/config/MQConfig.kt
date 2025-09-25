package no.nav.sokos.skattekort.config

import com.ibm.mq.constants.MQConstants
import com.ibm.mq.jakarta.jms.MQConnectionFactory
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import jakarta.jms.ConnectionFactory

private const val UTF_8_WITH_PUA = 1208

object MQConfig {
    val connectionFactory: ConnectionFactory by lazy {
        initConncetionFactory(PropertiesConfig.getMQProperties())
    }

    fun initConncetionFactory(properties: PropertiesConfig.MQProperties) =
        MQConnectionFactory().apply {
            transportType = WMQConstants.WMQ_CM_CLIENT
            queueManager = properties.mqQueueManagerName
            hostName = properties.hostname
            port = properties.port
            channel = properties.mqChannelName
            ccsid = UTF_8_WITH_PUA
            clientReconnectOptions = WMQConstants.WMQ_CLIENT_RECONNECT_Q_MGR
            setStringProperty(WMQConstants.WMQ_APPLICATIONNAME, PropertiesConfig.getApplicationProperties().naisAppName)
            setIntProperty(WMQConstants.JMS_IBM_ENCODING, MQConstants.MQENC_NATIVE)
            setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, UTF_8_WITH_PUA)
            setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, properties.userAuth)
        }
}
