package de.verdox.pv_miner;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;

@Push(value = PushMode.AUTOMATIC, transport = Transport.WEBSOCKET)
@PWA(name = "Solarminer.app - Mining with solar power", shortName = "Solarminer.app", manifestPath = "manifest.json")
public class VaadinAppShell implements AppShellConfigurator {

}