package org.leralix.tanpl3xmap;

import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polygon;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.registry.IconRegistry;
import net.pl3x.map.core.registry.Registry;
import net.pl3x.map.core.registry.WorldRegistry;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.leralix.lib.position.Vector2D;
import org.leralix.lib.position.Vector3D;
import org.leralix.tancommon.TownsAndNationsMapCommon;
import org.leralix.tancommon.markers.CommonMarkerRegister;
import org.leralix.tancommon.markers.IconType;
import org.leralix.tancommon.storage.Constants;
import org.leralix.tancommon.storage.PolygonCoordinate;
import org.leralix.tancommon.storage.TanKey;
import org.tan.api.interfaces.TanFort;
import org.tan.api.interfaces.TanLandmark;
import org.tan.api.interfaces.TanProperty;
import org.tan.api.interfaces.TanTerritory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

public class Pl3xmapMarkerRegister extends CommonMarkerRegister {

    private final Pl3xMap api;
    private final WorldRegistry worldRegistry;
    private final IconRegistry iconRegistry;

    private final Map<TanKey, SimpleLayer> chunkLayerMap = new HashMap<>();
    private final Map<TanKey, SimpleLayer> landmarkLayerMap = new HashMap<>();
    private final Map<TanKey, SimpleLayer> fortLayerMap = new HashMap<>();
    private final Map<TanKey, SimpleLayer> propertyLayerMap = new HashMap<>();

    public Pl3xmapMarkerRegister() {
        super();
        this.api = Pl3xMap.api();
        this.worldRegistry = api.getWorldRegistry();
        this.iconRegistry = api.getIconRegistry();
    }

    @Override
    protected void setupLandmarkLayer(String id, String name, int minZoom, int chunkLayerPriority, boolean hideByDefault, List<String> worldsName) {
        setupLayer(id, name, chunkLayerPriority, hideByDefault, worldsName, landmarkLayerMap);
    }

    @Override
    protected void setupChunkLayer(String id, String name, int minZoom, int chunkLayerPriority, boolean hideByDefault, List<String> worldsName) {
        setupLayer(id, name, chunkLayerPriority, hideByDefault, worldsName, chunkLayerMap);
    }

    @Override
    protected void setupFortLayer(String id, String name, int minZoom, int chunkLayerPriority, boolean hideByDefault, List<String> worldsName) {
        setupLayer(id, name, chunkLayerPriority, hideByDefault, worldsName, fortLayerMap);
    }

    @Override
    protected void setupPropertyLayer(String id, String name, int minZoom, int chunkLayerPriority, boolean hideByDefault, List<String> worldsName) {
        setupLayer(id, name, chunkLayerPriority, hideByDefault, worldsName, propertyLayerMap);
    }

    private void setupLayer(String id, String name, int priority, boolean hideByDefault, List<String> worldsName, Map<TanKey, SimpleLayer> layerMap) {
        List<org.bukkit.World> worlds = new ArrayList<>();
        if (worldsName.contains("all") || worldsName.isEmpty()) {
            worlds.addAll(Bukkit.getWorlds());
        } else {
            for (String worldName : worldsName) {
                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    worlds.add(world);
                }
            }
        }

        for (org.bukkit.World bukkitWorld : worlds) {
            World pl3xWorld = worldRegistry.get(bukkitWorld.getName());
            if (pl3xWorld == null) {
                continue;
            }

            Registry<Layer> layerRegistry = pl3xWorld.getLayerRegistry();
            layerRegistry.unregister(id);

            SimpleLayer layer = new SimpleLayer(id, () -> name);
            layer.setPriority(priority);
            layer.setDefaultHidden(hideByDefault);

            layerRegistry.register(id, layer);
            layerMap.put(new TanKey(bukkitWorld), layer);
        }
    }

    @Override
    public boolean isWorking() {
        return api != null;
    }

    @Override
    public void registerNewLandmark(TanLandmark landmark) {
        Location location = landmark.getLocation();
        org.bukkit.World world = location.getWorld();
        if (world == null) {
            return;
        }

        SimpleLayer layer = landmarkLayerMap.get(new TanKey(world));
        if (layer == null) {
            return;
        }

        String imageKey = landmark.isOwned() ? IconType.LANDMARK_CLAIMED.getFileName() : IconType.LANDMARK_UNCLAIMED.getFileName();
        Marker<?> marker = Marker.icon(landmark.getID(), Point.of(location.getX(), location.getZ()), imageKey, 16);
        marker.setOptions(Options.builder()
                .tooltipContent(landmark.getName())
                .popupContent(generateDescription(landmark))
        );

        layer.removeMarker(landmark.getID());
        layer.addMarker(marker);
    }

    @Override
    public void registerNewFort(TanFort fort) {
        Location location = fort.getFlagPosition().getLocation();
        org.bukkit.World world = location.getWorld();
        if (world == null) {
            return;
        }

        SimpleLayer layer = fortLayerMap.get(new TanKey(world));
        if (layer == null) {
            return;
        }

        Marker<?> marker = Marker.icon(fort.getID(), Point.of(location.getX(), location.getZ()), IconType.FORT.getFileName(), 16);
        marker.setOptions(Options.builder()
                .tooltipContent(fort.getName())
                .popupContent(generateDescription(fort))
        );

        layer.removeMarker(fort.getID());
        layer.addMarker(marker);
    }

    @Override
    public void registerNewProperty(TanProperty tanProperty) {
        Vector3D p1 = tanProperty.getFirstCorner();
        Vector3D p2 = tanProperty.getSecondCorner();

        org.bukkit.World world = p1.getWorld();
        if (world == null) {
            return;
        }

        SimpleLayer layer = propertyLayerMap.get(new TanKey(world));
        if (layer == null) {
            return;
        }

        PolygonCoordinate boundaries = getPolygonCoordinate(p1, p2);
        Polygon polygon = createPolygon(tanProperty.getID(), boundaries, List.of());

        int rgb = Constants.getPropertyColor(tanProperty);
        int strokeColor = 0xFF000000 | rgb;
        int fillColor = 0x66000000 | rgb;

        polygon.setOptions(Options.builder()
                .strokeColor(strokeColor)
                .strokeWeight(2)
                .fillColor(fillColor)
                .tooltipContent(tanProperty.getName())
                .popupContent(generateDescription(tanProperty))
        );

        layer.removeMarker(tanProperty.getID());
        layer.addMarker(polygon);
    }

    @Override
    public void registerNewArea(String polyid, TanTerritory territoryData, boolean b, String worldName, PolygonCoordinate coordinates, String infoWindowPopup, Collection<PolygonCoordinate> holes) {
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }

        SimpleLayer layer = chunkLayerMap.get(new TanKey(world));
        if (layer == null) {
            return;
        }

        Polygon polygon = createPolygon(polyid, coordinates, holes);

        int rgb = territoryData.getColor().asRGB();
        int strokeColor = 0xFF000000 | rgb;
        int fillColor = 0x80000000 | rgb;

        polygon.setOptions(Options.builder()
                .strokeColor(strokeColor)
                .strokeWeight(2)
                .fillColor(fillColor)
                .tooltipContent(territoryData.getName())
                .popupContent(infoWindowPopup)
        );

        layer.removeMarker(polyid);
        layer.addMarker(polygon);
    }

    private Polygon createPolygon(String key, PolygonCoordinate outer, Collection<PolygonCoordinate> holes) {
        List<Polyline> polylines = new ArrayList<>();
        polylines.add(createPolyline(key + "_outer", outer));

        int i = 0;
        for (PolygonCoordinate hole : holes) {
            polylines.add(createPolyline(key + "_hole_" + i, hole));
            i++;
        }

        return Marker.polygon(key, polylines);
    }

    private static Polyline createPolyline(String key, PolygonCoordinate coordinates) {
        List<Point> points = new ArrayList<>();
        int[] x = coordinates.getX();
        int[] z = coordinates.getZ();
        for (int i = 0; i < x.length; i++) {
            points.add(Point.of(x[i], z[i]));
        }
        return Polyline.of(key, points);
    }

    @Override
    public void deleteAllMarkers() {
        for (SimpleLayer layer : chunkLayerMap.values()) {
            layer.clearMarkers();
        }
        for (SimpleLayer layer : landmarkLayerMap.values()) {
            layer.clearMarkers();
        }
        for (SimpleLayer layer : fortLayerMap.values()) {
            layer.clearMarkers();
        }
        for (SimpleLayer layer : propertyLayerMap.values()) {
            layer.clearMarkers();
        }
    }

    @Override
    public void registerIcon(IconType iconType) {
        try {
            File file = new File(TownsAndNationsMapCommon.getPlugin().getDataFolder(), "icons/" + iconType.getFileName());
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return;
            }

            String fileName = iconType.getFileName();
            String type = fileName.substring(fileName.lastIndexOf('.') + 1);
            iconRegistry.register(new IconImage(fileName, image, type));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void registerCapital(String townName, Vector2D capitalPosition) {
        org.bukkit.World world = capitalPosition.getWorld();
        if (world == null) {
            return;
        }

        SimpleLayer layer = fortLayerMap.get(new TanKey(world));
        if (layer == null) {
            return;
        }

        String id = format(townName);
        Marker<?> marker = Marker.icon(id, Point.of(capitalPosition.getX() * 16 + 8, capitalPosition.getZ() * 16 + 8), IconType.CAPITAL.getFileName(), 16);
        marker.setOptions(Options.builder().tooltipContent(townName).popupContent(townName));

        layer.removeMarker(id);
        layer.addMarker(marker);
    }

    private String format(String value) {
        if (value == null) {
            return "Empty name";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "");
    }
}
