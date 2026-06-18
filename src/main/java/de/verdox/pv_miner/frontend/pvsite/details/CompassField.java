package de.verdox.pv_miner.frontend.pvsite.details;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;

@CssImport("./themes/solarminer/compass.css")
public class CompassField extends CustomField<Double> {

    private final Div dial = new Div();
    private final Div valueDisplay = new Div();
    private Double currentAngle = 180.0;

    public CompassField() {
        Div container = new Div();
        container.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("width", "100%");

        dial.addClassName("compass-dial");

        Div n = new Div(); n.setText("N"); n.addClassNames("compass-label", "n");
        Div e = new Div(); e.setText("O"); e.addClassNames("compass-label", "e");
        Div s = new Div(); s.setText("S"); s.addClassNames("compass-label", "s");
        Div w = new Div(); w.setText("W"); w.addClassNames("compass-label", "w");

        Div needle = new Div(); needle.addClassName("compass-needle");
        Div center = new Div(); center.addClassName("compass-center");

        dial.add(n, e, s, w, needle, center);
        valueDisplay.addClassName("compass-value-display");

        container.add(dial, valueDisplay);
        add(container);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        initCompassJS();
        updateNeedleUI(currentAngle);
        updateValueDisplay();
    }

    private void initCompassJS() {
        UI.getCurrent().getPage().executeJs(
                "const dial = $0;" +
                        "const needle = dial.querySelector('.compass-needle');" +
                        "let isDragging = false;" +
                        "let currentRotation = 180;" +

                        "dial.setNeedle = function(targetDeg) {" +
                        "   let currentMod = (currentRotation % 360 + 360) % 360;" +
                        "   let delta = targetDeg - currentMod;" +
                        "   if (delta > 180) delta -= 360;" +
                        "   else if (delta < -180) delta += 360;" +
                        "   currentRotation += delta;" +
                        "   needle.style.transform = `translateX(-50%) rotate(${currentRotation}deg)`;" +
                        "};" +

                        "function updateAngle(e) {" +
                        "   const rect = dial.getBoundingClientRect();" +
                        "   const cx = rect.left + rect.width / 2;" +
                        "   const cy = rect.top + rect.height / 2;" +
                        "   const clientX = e.touches ? e.touches[0].clientX : e.clientX;" +
                        "   const clientY = e.touches ? e.touches[0].clientY : e.clientY;" +

                        "   let radians = Math.atan2(clientY - cy, clientX - cx);" +
                        "   let rawDeg = (radians * (180 / Math.PI)) + 90;" +

                        "   let normalizedDeg = Math.round((rawDeg % 360 + 360) % 360);" +

                        "   dial.setNeedle(normalizedDeg);" +
                        "   $1.$server.updateValueFromClient(normalizedDeg);" +
                        "}" +

                        "dial.addEventListener('mousedown', (e) => { isDragging = true; updateAngle(e); });" +
                        "window.addEventListener('mousemove', (e) => { if(isDragging) updateAngle(e); });" +
                        "window.addEventListener('mouseup', () => { isDragging = false; });" +

                        "dial.addEventListener('touchstart', (e) => { isDragging = true; updateAngle(e); });" +
                        "window.addEventListener('touchmove', (e) => { if(isDragging){ e.preventDefault(); updateAngle(e); } }, {passive: false});" +
                        "window.addEventListener('touchend', () => { isDragging = false; });",
                dial, getElement()
        );
    }

    @ClientCallable
    public void updateValueFromClient(double degree) {
        this.currentAngle = degree;
        updateValueDisplay();
        updateNeedleUI(degree);

        setModelValue(degree, true);
    }

    @Override
    protected Double generateModelValue() {
        return currentAngle;
    }

    @Override
    protected void setPresentationValue(Double newPresentationValue) {
        this.currentAngle = newPresentationValue != null ? newPresentationValue : 180.0;
        updateValueDisplay();
        updateNeedleUI(this.currentAngle);
    }

    private void updateNeedleUI(double degree) {
        UI.getCurrent().getPage().executeJs("if($0.setNeedle) $0.setNeedle($1);", dial, degree);
    }

    private void updateValueDisplay() {
        String direction = getDirectionString(currentAngle);
        valueDisplay.setText(String.format("%.0f° (%s)", currentAngle, direction));
    }

    private String getDirectionString(double degree) {
        if (degree >= 337.5 || degree < 22.5) return "North";
        if (degree >= 22.5 && degree < 67.5) return "North-East";
        if (degree >= 67.5 && degree < 112.5) return "East";
        if (degree >= 112.5 && degree < 157.5) return "South-East";
        if (degree >= 157.5 && degree < 202.5) return "South";
        if (degree >= 202.5 && degree < 247.5) return "South-West";
        if (degree >= 247.5 && degree < 292.5) return "West";
        return "North-West";
    }
}
