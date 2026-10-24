package com.agricontract.product.application.kml;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class KmlPlacemarkReaderTest {

    private static List<KmlPlacemark> read(String placemarks) {
        String kml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <kml xmlns="http://www.opengis.net/kml/2.2"><Document><Folder>%s</Folder></Document></kml>
                """.formatted(placemarks);
        return KmlPlacemarkReader.read(kml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void point_withAreaInExtendedData() {
        List<KmlPlacemark> result = read("""
                <Placemark><name>Hộ Lê Văn A</name>
                  <ExtendedData><Data name="area_ha"><value>1.5</value></Data></ExtendedData>
                  <Point><coordinates>108.050123,12.666789,0</coordinates></Point>
                </Placemark>""");

        assertThat(result).hasSize(1);
        KmlPlacemark placemark = result.get(0);
        assertThat(placemark.error()).isNull();
        assertThat(placemark.name()).isEqualTo("Hộ Lê Văn A");
        assertThat(placemark.kind()).isEqualTo(KmlPlacemark.Kind.POINT);
        assertThat(placemark.shell().get(0)).containsExactly(108.050123, 12.666789);
        assertThat(placemark.areaHectares()).isEqualByComparingTo("1.5");
    }

    @Test
    void point_areaFromSchemaDataWithDecimalComma() {
        KmlPlacemark placemark = read("""
                <Placemark><name>Ho B</name>
                  <ExtendedData><SchemaData schemaUrl="#s"><SimpleData name="dien_tich">2,25</SimpleData></SchemaData></ExtendedData>
                  <Point><coordinates>108.05,12.66</coordinates></Point>
                </Placemark>""").get(0);

        assertThat(placemark.areaHectares()).isEqualByComparingTo("2.25");
    }

    @Test
    void point_withoutArea_isReadWithNullArea() {
        KmlPlacemark placemark = read("""
                <Placemark><name>Ho C</name><Point><coordinates>108.05,12.66</coordinates></Point></Placemark>""").get(0);

        assertThat(placemark.error()).isNull();
        assertThat(placemark.areaHectares()).isNull();
    }

    @Test
    void polygon_withOuterAndInnerRings() {
        KmlPlacemark placemark = read("""
                <Placemark><name>Ho D</name><Polygon>
                  <outerBoundaryIs><LinearRing><coordinates>
                    108.05,12.66 108.053,12.66 108.053,12.663 108.05,12.663 108.05,12.66
                  </coordinates></LinearRing></outerBoundaryIs>
                  <innerBoundaryIs><LinearRing><coordinates>
                    108.051,12.661 108.051,12.662 108.052,12.662 108.051,12.661
                  </coordinates></LinearRing></innerBoundaryIs>
                </Polygon></Placemark>""").get(0);

        assertThat(placemark.error()).isNull();
        assertThat(placemark.kind()).isEqualTo(KmlPlacemark.Kind.POLYGON);
        assertThat(placemark.shell()).hasSize(5);
        assertThat(placemark.holes()).hasSize(1);
        assertThat(placemark.holes().get(0)).hasSize(4);
    }

    @Test
    void badPlacemarks_areRejectedOneByOne_andTheGoodOneStillReads() {
        List<KmlPlacemark> result = read("""
                <Placemark><Point><coordinates>108.05,12.66</coordinates></Point></Placemark>
                <Placemark><name>Multi</name><MultiGeometry><Point><coordinates>108.05,12.66</coordinates></Point></MultiGeometry></Placemark>
                <Placemark><name>Line</name><LineString><coordinates>108.05,12.66 108.06,12.67</coordinates></LineString></Placemark>
                <Placemark><name>Bad number</name><Point><coordinates>108.05,abc</coordinates></Point></Placemark>
                <Placemark><name>No geometry</name></Placemark>
                <Placemark><name>Good</name><Point><coordinates>108.05,12.66</coordinates></Point></Placemark>""");

        assertThat(result).extracting(KmlPlacemark::kind).containsExactly(
                KmlPlacemark.Kind.REJECTED, KmlPlacemark.Kind.REJECTED, KmlPlacemark.Kind.REJECTED,
                KmlPlacemark.Kind.REJECTED, KmlPlacemark.Kind.REJECTED, KmlPlacemark.Kind.POINT);
        assertThat(result.get(0).name()).isEqualTo("#1");
        assertThat(result.get(0).error()).contains("<name>");
        assertThat(result.get(1).error()).contains("MultiGeometry");
        assertThat(result.get(3).error()).contains("not a number");
    }

    @Test
    void doctype_isRefused_soNoExternalEntityIsEverResolved() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE kml [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <kml><Document><Placemark><name>&secret;</name></Placemark></Document></kml>""";

        assertThatThrownBy(() -> KmlPlacemarkReader.read(xxe.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not well-formed");
    }

    @Test
    void notKml_isRefused() {
        assertThatThrownBy(() -> KmlPlacemarkReader.read("<gpx/>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("<kml>");
        assertThatThrownBy(() -> KmlPlacemarkReader.read("not xml".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
