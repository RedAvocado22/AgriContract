package com.agricontract.product.application.kml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the placemarks of a KML file (design §2.2/§4). Each placemark becomes one {@link KmlPlacemark}; a
 * placemark that cannot be read carries an error instead of failing the whole file.
 */
public final class KmlPlacemarkReader {

    // ExtendedData field names the seller guide asks for, since a point has no area of its own (design §4)
    private static final Set<String> AREA_FIELDS = Set.of("area_ha", "dien_tich");

    private KmlPlacemarkReader() {
    }

    /**
     * @throws IllegalArgumentException when the file is not well-formed KML
     */
    public static List<KmlPlacemark> read(byte[] kml) {
        Document document = parse(kml);
        if (!"kml".equals(document.getDocumentElement().getLocalName())) {
            throw new IllegalArgumentException("Not a KML document: root element must be <kml>");
        }
        NodeList placemarks = document.getElementsByTagNameNS("*", "Placemark");
        List<KmlPlacemark> result = new ArrayList<>(placemarks.getLength());
        for (int i = 0; i < placemarks.getLength(); i++) {
            result.add(placemark((Element) placemarks.item(i), i + 1));
        }
        return result;
    }

    private static KmlPlacemark placemark(Element placemark, int position) {
        String name = childText(placemark, "name");
        String label = name == null || name.isBlank() ? "#" + position : name.trim();
        try {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Placemark has no <name>; it is used as the household label");
            }
            BigDecimal area = areaHectares(placemark);
            List<Element> geometries = new ArrayList<>();
            for (String tag : List.of("Point", "Polygon", "MultiGeometry", "LineString")) {
                geometries.addAll(children(placemark, tag));
            }
            if (geometries.size() != 1) {
                throw new IllegalArgumentException("Placemark must contain exactly one Point or Polygon");
            }
            Element geometry = geometries.get(0);
            return switch (geometry.getLocalName()) {
                case "Point" -> KmlPlacemark.point(label, single(coordinates(geometry)), area);
                case "Polygon" -> KmlPlacemark.polygon(label, ring(geometry, "outerBoundaryIs"), holes(geometry), area);
                default -> throw new IllegalArgumentException(geometry.getLocalName() + " is not supported; use one Point or Polygon");
            };
        } catch (IllegalArgumentException e) {
            return KmlPlacemark.rejected(label, e.getMessage());
        }
    }

    private static List<double[]> ring(Element polygon, String boundary) {
        List<Element> boundaries = children(polygon, boundary);
        if (boundaries.size() != 1) {
            throw new IllegalArgumentException("Polygon must have exactly one <" + boundary + ">");
        }
        return linearRing(boundaries.get(0));
    }

    private static List<List<double[]>> holes(Element polygon) {
        return children(polygon, "innerBoundaryIs").stream().map(KmlPlacemarkReader::linearRing).toList();
    }

    private static List<double[]> linearRing(Element boundary) {
        List<Element> rings = children(boundary, "LinearRing");
        if (rings.size() != 1) {
            throw new IllegalArgumentException("Polygon boundary must contain one <LinearRing>");
        }
        return coordinates(rings.get(0));
    }

    // KML <coordinates>: whitespace-separated "longitude,latitude[,altitude]" tuples
    private static List<double[]> coordinates(Element geometry) {
        String text = childText(geometry, "coordinates");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Missing <coordinates>");
        }
        List<double[]> points = new ArrayList<>();
        for (String tuple : text.trim().split("\\s+")) {
            String[] parts = tuple.split(",");
            if (parts.length < 2 || parts.length > 3) {
                throw new IllegalArgumentException("Bad coordinate '" + tuple + "': expected longitude,latitude[,altitude]");
            }
            try {
                points.add(new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])});
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad coordinate '" + tuple + "': not a number");
            }
        }
        return points;
    }

    private static double[] single(List<double[]> points) {
        if (points.size() != 1) {
            throw new IllegalArgumentException("A Point must have exactly one coordinate");
        }
        return points.get(0);
    }

    private static BigDecimal areaHectares(Element placemark) {
        for (Element extended : children(placemark, "ExtendedData")) {
            // <Data name="area_ha"><value>…</value></Data> or <SchemaData><SimpleData name="area_ha">…</SimpleData>
            for (Element data : descendants(extended, "Data")) {
                if (AREA_FIELDS.contains(data.getAttribute("name"))) {
                    return number(childText(data, "value"), data.getAttribute("name"));
                }
            }
            for (Element data : descendants(extended, "SimpleData")) {
                if (AREA_FIELDS.contains(data.getAttribute("name"))) {
                    return number(data.getTextContent(), data.getAttribute("name"));
                }
            }
        }
        return null;
    }

    // accepts a Vietnamese decimal comma ("1,5")
    private static BigDecimal number(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("ExtendedData " + field + " is empty");
        }
        try {
            return new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ExtendedData " + field + " is not a number: " + text.trim());
        }
    }

    private static String childText(Element parent, String localName) {
        List<Element> found = children(parent, localName);
        return found.isEmpty() ? null : found.get(0).getTextContent();
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static List<Element> descendants(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        List<Element> result = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }

    // Untrusted XML: no DTD, no external entities (XXE), no entity expansion.
    private static Document parse(byte[] kml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(kml));
        } catch (SAXException | IOException e) {
            throw new IllegalArgumentException("KML is not well-formed XML: " + e.getMessage());
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML parser does not support secure processing", e);
        }
    }
}
