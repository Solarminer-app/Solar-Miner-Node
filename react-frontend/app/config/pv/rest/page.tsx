import ConfigEditor from '../config-editor';

export default function RestPVConfigPage() {
    return (
        <ConfigEditor
            headerSubtitleKey="config.subtitle.rest"
            headerTitleKey="config.title.rest"
            protocol="rest"
        />
    );
}
