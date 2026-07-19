import ConfigEditor from '../../config-editor';

export default function ModbusRTUConfigPage() {
    return <ConfigEditor headerSubtitleKey="config.subtitle.modbus_rtu" headerTitleKey="config.title.modbus_rtu" protocol="modbus-rtu"/>;
}
