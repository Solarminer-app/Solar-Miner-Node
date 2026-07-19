import ConfigEditor from '../config-editor';

export default function WebSocketConfigPage() {
    return <ConfigEditor headerSubtitleKey="config.subtitle.websocket" headerTitleKey="config.title.websocket" protocol="websocket"/>;
}
