import ConfigEditor from '../../config-editor';

export default function ModbusTCPConfigPage() {
    return (
        <ConfigEditor
            headerSubtitleKey="config.subtitle.modbus"
            headerTitleKey="config.title.modbus"
            protocol="modbus"
        />
    );
}
