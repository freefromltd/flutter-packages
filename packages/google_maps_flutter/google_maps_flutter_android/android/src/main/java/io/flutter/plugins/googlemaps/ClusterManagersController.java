// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.googlemaps;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.AdvancedMarkerOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm;
import com.google.maps.android.clustering.view.ClusterRenderer;
import com.google.maps.android.clustering.view.DefaultAdvancedMarkersClusterRenderer;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.google.maps.android.collections.MarkerManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.Result;
import kotlin.Unit;

/**
 * Controls cluster managers and exposes interfaces for adding and removing cluster items for
 * specific cluster managers.
 */
class ClusterManagersController
    implements GoogleMap.OnCameraIdleListener,
        ClusterManager.OnClusterClickListener<MarkerBuilder> {
  private final @NonNull Context context;

  @VisibleForTesting @NonNull
  protected final HashMap<String, ClusterManager<MarkerBuilder>> clusterManagerIdToManager;

  private final @NonNull MapsCallbackApi flutterApi;
  private @Nullable MarkerManager markerManager;
  private @Nullable GoogleMap googleMap;
  private final @NonNull PlatformMarkerType markerType;

  @Nullable
  private ClusterManager.OnClusterItemClickListener<MarkerBuilder> clusterItemClickListener;

  @Nullable
  private ClusterManager.OnClusterItemInfoWindowClickListener<MarkerBuilder>
      clusterItemInfoWindowClickListener;

  @Nullable
  private ClusterManagersController.OnClusterItemRendered<MarkerBuilder>
      clusterItemRenderedListener;

  ClusterManagersController(
      @NonNull MapsCallbackApi flutterApi,
      @NonNull Context context,
      @NonNull PlatformMarkerType markerType) {
    this.clusterManagerIdToManager = new HashMap<>();
    this.context = context;
    this.flutterApi = flutterApi;
    this.markerType = markerType;
  }

  void init(GoogleMap googleMap, MarkerManager markerManager) {
    this.markerManager = markerManager;
    this.googleMap = googleMap;
  }

  void setClusterItemClickListener(
      @Nullable ClusterManager.OnClusterItemClickListener<MarkerBuilder> listener) {
    clusterItemClickListener = listener;
    initListenersForClusterManagers();
  }

  void setClusterItemInfoWindowClickListener(
      @Nullable ClusterManager.OnClusterItemInfoWindowClickListener<MarkerBuilder> listener) {
    clusterItemInfoWindowClickListener = listener;
    initListenersForClusterManagers();
  }

  void setClusterItemRenderedListener(
      @Nullable ClusterManagersController.OnClusterItemRendered<MarkerBuilder> listener) {
    clusterItemRenderedListener = listener;
  }

  private void initListenersForClusterManagers() {
    for (Map.Entry<String, ClusterManager<MarkerBuilder>> entry :
        clusterManagerIdToManager.entrySet()) {
      initListenersForClusterManager(
          entry.getValue(), this, clusterItemClickListener, clusterItemInfoWindowClickListener);
    }
  }

  private void initListenersForClusterManager(
      ClusterManager<MarkerBuilder> clusterManager,
      @Nullable ClusterManager.OnClusterClickListener<MarkerBuilder> clusterClickListener,
      @Nullable ClusterManager.OnClusterItemClickListener<MarkerBuilder> clusterItemClickListener,
      @Nullable
          ClusterManager.OnClusterItemInfoWindowClickListener<MarkerBuilder>
              clusterItemInfoWindowClickListener) {
    clusterManager.setOnClusterClickListener(clusterClickListener);
    clusterManager.setOnClusterItemClickListener(clusterItemClickListener);
    clusterManager.setOnClusterItemInfoWindowClickListener(clusterItemInfoWindowClickListener);
  }

  /** Adds new ClusterManagers to the controller. */
  void addClusterManagers(@NonNull List<PlatformClusterManager> clusterManagersToAdd) {
    for (PlatformClusterManager clusterToAdd : clusterManagersToAdd) {
      addClusterManager(clusterToAdd);
    }
  }

  void addClusterManager(@NonNull PlatformClusterManager clusterToAdd) {
    ClusterManager<MarkerBuilder> clusterManager =
        clusterManagerIdToManager.get(clusterToAdd.getIdentifier());
    if (clusterManager != null) {
      if (clusterToAdd.getMaxDistance() != null) {
        NonHierarchicalDistanceBasedAlgorithm<MarkerBuilder> algorithm =
            new NonHierarchicalDistanceBasedAlgorithm<>();
        algorithm.setMaxDistanceBetweenClusteredItems((int) Math.round(clusterToAdd.getMaxDistance()));
        clusterManager.setAlgorithm(algorithm);
      }
      ClusterRenderer<MarkerBuilder> renderer = clusterManager.getRenderer();
      if (renderer instanceof MarkerClusterRenderer) {
        ((MarkerClusterRenderer<MarkerBuilder>) renderer).setClusterStyle(clusterToAdd);
      } else if (renderer instanceof AdvancedMarkerClusterRenderer) {
        ((AdvancedMarkerClusterRenderer<MarkerBuilder>) renderer).setClusterStyle(clusterToAdd);
      }
      Long minClusterSize = clusterToAdd.getMinClusterSize();
      if (minClusterSize != null) {
        if (renderer instanceof DefaultClusterRenderer) {
          ((DefaultClusterRenderer<MarkerBuilder>) renderer).setMinClusterSize(minClusterSize.intValue());
        }
      }
      clusterManager.cluster();
      return;
    }

    clusterManager = new ClusterManager<>(context, googleMap, markerManager);
    if (clusterToAdd.getMaxDistance() != null) {
      NonHierarchicalDistanceBasedAlgorithm<MarkerBuilder> algorithm =
          new NonHierarchicalDistanceBasedAlgorithm<>();
      algorithm.setMaxDistanceBetweenClusteredItems((int) Math.round(clusterToAdd.getMaxDistance()));
      clusterManager.setAlgorithm(algorithm);
    }
    initializeRenderer(clusterManager, clusterToAdd);
    clusterManagerIdToManager.put(clusterToAdd.getIdentifier(), clusterManager);
  }

  private void initializeRenderer(ClusterManager<MarkerBuilder> clusterManager, PlatformClusterManager clusterToAdd) {
    final ClusterRenderer<MarkerBuilder> clusterRenderer =
        switch (markerType) {
          case ADVANCED_MARKER ->
              new AdvancedMarkerClusterRenderer<>(context, googleMap, clusterManager, this, clusterToAdd);
          default -> new MarkerClusterRenderer<>(context, googleMap, clusterManager, this, clusterToAdd);
        };
    Long minClusterSize = clusterToAdd.getMinClusterSize();
    if (minClusterSize != null) {
      if (clusterRenderer instanceof DefaultClusterRenderer) {
        ((DefaultClusterRenderer<MarkerBuilder>) clusterRenderer).setMinClusterSize(minClusterSize.intValue());
      }
    }
    clusterManager.setRenderer(clusterRenderer);
    initListenersForClusterManager(
        clusterManager, this, clusterItemClickListener, clusterItemInfoWindowClickListener);
  }

  /** Removes ClusterManagers by given cluster manager IDs from the controller. */
  public void removeClusterManagers(@NonNull List<String> clusterManagerIdsToRemove) {
    for (String clusterManagerId : clusterManagerIdsToRemove) {
      removeClusterManager(clusterManagerId);
    }
  }

  /**
   * Removes the ClusterManagers by the given cluster manager ID from the controller. The reference
   * to this cluster manager is removed from the clusterManagerIdToManager and it will be garbage
   * collected later.
   */
  private void removeClusterManager(String clusterManagerId) {
    // Remove the cluster manager from the hash map to allow it to be garbage collected.
    final ClusterManager<MarkerBuilder> clusterManager =
        clusterManagerIdToManager.remove(clusterManagerId);
    if (clusterManager == null) {
      return;
    }
    initListenersForClusterManager(clusterManager, null, null, null);
    clusterManager.clearItems();
    clusterManager.cluster();
  }

  /** Adds item to the ClusterManager it belongs to. */
  public void addItem(MarkerBuilder item) {
    ClusterManager<MarkerBuilder> clusterManager =
        clusterManagerIdToManager.get(item.clusterManagerId());
    if (clusterManager != null) {
      clusterManager.addItem(item);
      clusterManager.cluster();
    }
  }

  /** Adds multiple items to the ClusterManager with the given ID. */
  public void addItems(String clusterManagerId, @NonNull List<MarkerBuilder> items) {
    ClusterManager<MarkerBuilder> clusterManager = clusterManagerIdToManager.get(clusterManagerId);
    if (clusterManager != null) {
      clusterManager.addItems(items);
      clusterManager.cluster();
    }
  }

  /** Removes item from the ClusterManager it belongs to. */
  public void removeItem(MarkerBuilder item) {
    ClusterManager<MarkerBuilder> clusterManager =
        clusterManagerIdToManager.get(item.clusterManagerId());
    if (clusterManager != null) {
      clusterManager.removeItem(item);
      clusterManager.cluster();
    }
  }

  /** Removes multiple items from the ClusterManager with the given ID. */
  public void removeItems(String clusterManagerId, @NonNull List<MarkerBuilder> items) {
    ClusterManager<MarkerBuilder> clusterManager = clusterManagerIdToManager.get(clusterManagerId);
    if (clusterManager != null) {
      clusterManager.removeItems(items);
      clusterManager.cluster();
    }
  }

  /** Called when ClusterRenderer has rendered new visible marker to the map. */
  void onClusterItemRendered(@NonNull MarkerBuilder item, @NonNull Marker marker) {
    // If map is being disposed, clusterItemRenderedListener might have been cleared and
    // set to null.
    if (clusterItemRenderedListener != null) {
      clusterItemRenderedListener.onClusterItemRendered(item, marker);
    }
  }

  /**
   * Requests all current clusters from the algorithm of the requested ClusterManager and converts
   * them to result response.
   */
  public @NonNull Set<? extends Cluster<MarkerBuilder>> getClustersWithClusterManagerId(
      String clusterManagerId) {
    ClusterManager<MarkerBuilder> clusterManager = clusterManagerIdToManager.get(clusterManagerId);
    if (clusterManager == null) {
      throw new FlutterError(
          "Invalid clusterManagerId",
          "getClusters called with invalid clusterManagerId:" + clusterManagerId,
          null);
    }
    return clusterManager.getAlgorithm().getClusters(googleMap.getCameraPosition().zoom);
  }

  @Override
  public void onCameraIdle() {
    for (Map.Entry<String, ClusterManager<MarkerBuilder>> entry :
        clusterManagerIdToManager.entrySet()) {
      entry.getValue().onCameraIdle();
    }
  }

  @Override
  public boolean onClusterClick(Cluster<MarkerBuilder> cluster) {
    if (cluster.getSize() > 0) {
      MarkerBuilder[] builders = cluster.getItems().toArray(new MarkerBuilder[0]);
      String clusterManagerId = builders[0].clusterManagerId();
      flutterApi.onClusterTap(
          Convert.clusterToPigeon(clusterManagerId, cluster),
          (Result<Unit> result) -> Unit.INSTANCE);
    }

    // Return false to allow the default behavior of the cluster click event to occur.
    return false;
  }

  /**
   * MarkerClusterRenderer builds marker options for new markers to be rendered to the map. After
   * cluster item (marker) is rendered, it is sent to the listeners for control.
   */
  @VisibleForTesting
  static class MarkerClusterRenderer<T extends MarkerBuilder> extends DefaultClusterRenderer<T> {
    private final Context context;
    private final ClusterManagersController clusterManagersController;
    private PlatformClusterManager clusterStyle;
    private final Map<Integer, com.google.android.gms.maps.model.BitmapDescriptor> iconCache = new HashMap<>();

    public MarkerClusterRenderer(
        Context context,
        GoogleMap map,
        ClusterManager<T> clusterManager,
        ClusterManagersController clusterManagersController,
        PlatformClusterManager clusterStyle) {
      super(context, map, clusterManager);
      this.context = context;
      this.clusterManagersController = clusterManagersController;
      this.clusterStyle = clusterStyle;
    }

    public void setClusterStyle(PlatformClusterManager clusterStyle) {
      this.clusterStyle = clusterStyle;
      this.iconCache.clear();
    }

    @Override
    protected void onBeforeClusterItemRendered(
        @NonNull T item, @NonNull MarkerOptions markerOptions) {
      item.update(markerOptions);
    }

    @Override
    protected void onClusterItemRendered(@NonNull T item, @NonNull Marker marker) {
      super.onClusterItemRendered(item, marker);
      clusterManagersController.onClusterItemRendered(item, marker);
    }

    @Override
    protected void onBeforeClusterRendered(
        @NonNull Cluster<T> cluster, @NonNull MarkerOptions markerOptions) {
      if (clusterStyle.getCoreColor() != null) {
        com.google.android.gms.maps.model.BitmapDescriptor icon =
            getClusterIcon(this.context, clusterStyle, cluster, iconCache);
        markerOptions.icon(icon);
      } else {
        super.onBeforeClusterRendered(cluster, markerOptions);
      }
    }

    @Override
    protected void onClusterUpdated(
        @NonNull Cluster<T> cluster, @NonNull Marker marker) {
      if (clusterStyle.getCoreColor() != null) {
        com.google.android.gms.maps.model.BitmapDescriptor icon =
            getClusterIcon(this.context, clusterStyle, cluster, iconCache);
        marker.setIcon(icon);
      } else {
        super.onClusterUpdated(cluster, marker);
      }
    }
  }

  @VisibleForTesting
  static class AdvancedMarkerClusterRenderer<T extends MarkerBuilder>
      extends DefaultAdvancedMarkersClusterRenderer<T> {
    private final Context context;
    private final ClusterManagersController clusterManagersController;
    private PlatformClusterManager clusterStyle;
    private final Map<Integer, com.google.android.gms.maps.model.BitmapDescriptor> iconCache = new HashMap<>();

    public AdvancedMarkerClusterRenderer(
        Context context,
        GoogleMap map,
        ClusterManager<T> clusterManager,
        ClusterManagersController clusterManagersController,
        PlatformClusterManager clusterStyle) {
      super(context, map, clusterManager);
      this.context = context;
      this.clusterManagersController = clusterManagersController;
      this.clusterStyle = clusterStyle;
    }

    public void setClusterStyle(PlatformClusterManager clusterStyle) {
      this.clusterStyle = clusterStyle;
      this.iconCache.clear();
    }

    @Override
    protected void onBeforeClusterItemRendered(
        @NonNull T item, @NonNull AdvancedMarkerOptions markerOptions) {
      item.update(markerOptions);
    }

    @Override
    protected void onClusterItemRendered(@NonNull T item, @NonNull Marker marker) {
      super.onClusterItemRendered(item, marker);
      clusterManagersController.onClusterItemRendered(item, marker);
    }

    @Override
    protected void onBeforeClusterRendered(
        @NonNull Cluster<T> cluster, @NonNull AdvancedMarkerOptions markerOptions) {
      if (clusterStyle.getCoreColor() != null) {
        com.google.android.gms.maps.model.BitmapDescriptor icon =
            getClusterIcon(this.context, clusterStyle, cluster, iconCache);
        markerOptions.icon(icon);
      } else {
        super.onBeforeClusterRendered(cluster, markerOptions);
      }
    }

    @Override
    protected void onClusterUpdated(
        @NonNull Cluster<T> cluster, @NonNull com.google.android.gms.maps.model.AdvancedMarker marker) {
      if (clusterStyle.getCoreColor() != null) {
        com.google.android.gms.maps.model.BitmapDescriptor icon =
            getClusterIcon(this.context, clusterStyle, cluster, iconCache);
        marker.setIcon(icon);
      } else {
        super.onClusterUpdated(cluster, marker);
      }
    }
  }

  static com.google.android.gms.maps.model.BitmapDescriptor getClusterIcon(
      Context context,
      PlatformClusterManager clusterStyle,
      Cluster<?> cluster,
      Map<Integer, com.google.android.gms.maps.model.BitmapDescriptor> iconCache) {
    int count = 0;
    for (Object item : cluster.getItems()) {
      if (item instanceof MarkerBuilder) {
        count += ((MarkerBuilder) item).getItemCount();
      } else {
        count += 1;
      }
    }
    com.google.android.gms.maps.model.BitmapDescriptor cached = iconCache.get(count);
    if (cached != null) {
      return cached;
    }

    float density = context.getResources().getDisplayMetrics().density;
    double circleSizeVal = clusterStyle.getCircleSize() != null ? clusterStyle.getCircleSize() : 44.0;
    circleSizeVal += 8.0;
    float sizePx = (float) (circleSizeVal * density);
    int size = Math.round(sizePx);

    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

    float radius = sizePx / 2.0f;

    android.graphics.Paint paint = new android.graphics.Paint();
    paint.setAntiAlias(true);
    paint.setStyle(android.graphics.Paint.Style.FILL);
    int outerRingColor = clusterStyle.getOuterRingColor() != null ? clusterStyle.getOuterRingColor().intValue() : 0x22007AFF;
    paint.setColor(outerRingColor);
    canvas.drawCircle(radius, radius, radius, paint);

    float outerRingWidth = 8.0f * density;
    float middleRadius = radius - outerRingWidth;
    if (middleRadius > 0) {
      int strokeColor = clusterStyle.getStrokeColor() != null ? clusterStyle.getStrokeColor().intValue() : 0xFFFFFFFF;
      paint.setColor(strokeColor);
      canvas.drawCircle(radius, radius, middleRadius, paint);
    }

    float strokeWidth = 2.0f * density;
    float innerRadius = middleRadius - strokeWidth;
    if (innerRadius > 0) {
      int coreColor = clusterStyle.getCoreColor() != null ? clusterStyle.getCoreColor().intValue() : 0xFF007AFF;
      paint.setColor(coreColor);
      canvas.drawCircle(radius, radius, innerRadius, paint);
    }

    paint.setColor(clusterStyle.getTextColor() != null ? clusterStyle.getTextColor().intValue() : 0xFFFFFFFF);
    double fontSizeVal = clusterStyle.getFontSize() != null ? clusterStyle.getFontSize() : 14.0;
    paint.setTextSize((float) (fontSizeVal * density));
    paint.setTextAlign(android.graphics.Paint.Align.CENTER);
    if (clusterStyle.getFontFamily() != null) {
      paint.setTypeface(android.graphics.Typeface.create(clusterStyle.getFontFamily(), android.graphics.Typeface.BOLD));
    } else {
      paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
    }

    float yOffset = (paint.descent() + paint.ascent()) / 2.0f;
    canvas.drawText(String.valueOf(count), radius, radius - yOffset, paint);

    com.google.android.gms.maps.model.BitmapDescriptor descriptor = com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
    iconCache.put(count, descriptor);
    return descriptor;
  }

  /** Interface for handling situations where clusterManager adds new visible marker to the map. */
  public interface OnClusterItemRendered<T extends ClusterItem> {
    void onClusterItemRendered(@NonNull T item, @NonNull Marker marker);
  }
}
