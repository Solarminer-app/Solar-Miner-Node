import ConfigEditor from '../config-editor';

export default function MqttConfigPage() {
    return <ConfigEditor headerSubtitleKey="config.subtitle.mqtt" headerTitleKey="config.title.mqtt" protocol="mqtt"/>;
}
