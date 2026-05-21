// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:ui' show Color;
import 'package:flutter/foundation.dart' show immutable;
import 'types.dart';

/// Uniquely identifies a [ClusterManager] among [GoogleMap] clusters.
///
/// This does not have to be globally unique, only unique among the list.
@immutable
class ClusterManagerId extends MapsObjectId<ClusterManager> {
  /// Creates an immutable identifier for a [ClusterManager].
  const ClusterManagerId(super.value);
}

@immutable
class ClusterStyle {
  const ClusterStyle({
    required this.coreColor,
    required this.strokeColor,
    required this.outerRingColor,
    this.textColor = const Color(0xFFFFFFFF),
    this.fontFamily,
    this.fontSize = 14.0,
    this.circleSize = 44.0,
  });

  final Color coreColor;
  final Color strokeColor;
  final Color outerRingColor;
  final Color textColor;
  final String? fontFamily;
  final double fontSize;
  final double circleSize;

  Object toJson() {
    final json = <String, Object>{};
    json['coreColor'] = coreColor.value;
    json['strokeColor'] = strokeColor.value;
    json['outerRingColor'] = outerRingColor.value;
    json['textColor'] = textColor.value;
    if (fontFamily != null) {
      json['fontFamily'] = fontFamily!;
    }
    json['fontSize'] = fontSize;
    json['circleSize'] = circleSize;
    return json;
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) {
      return true;
    }
    if (other.runtimeType != runtimeType) {
      return false;
    }
    return other is ClusterStyle &&
        coreColor == other.coreColor &&
        strokeColor == other.strokeColor &&
        outerRingColor == other.outerRingColor &&
        textColor == other.textColor &&
        fontFamily == other.fontFamily &&
        fontSize == other.fontSize &&
        circleSize == other.circleSize;
  }

  @override
  int get hashCode => Object.hash(
    coreColor,
    strokeColor,
    outerRingColor,
    textColor,
    fontFamily,
    fontSize,
    circleSize,
  );

  @override
  String toString() {
    return 'ClusterStyle{coreColor: $coreColor, strokeColor: $strokeColor, outerRingColor: $outerRingColor, textColor: $textColor, fontFamily: $fontFamily, fontSize: $fontSize, circleSize: $circleSize}';
  }
}

/// [ClusterManager] manages marker clustering for set of [Marker]s that have
/// the same [ClusterManagerId] set.
@immutable
class ClusterManager implements MapsObject<ClusterManager> {
  /// Creates an immutable object for managing clustering for set of markers.
  const ClusterManager({
    required this.clusterManagerId,
    this.onClusterTap,
    this.maxDistance,
    this.minClusterSize,
    this.style,
  });

  /// Uniquely identifies a [ClusterManager].
  final ClusterManagerId clusterManagerId;

  @override
  ClusterManagerId get mapsId => clusterManagerId;

  /// Callback to receive tap events for cluster markers placed on this map.
  final ArgumentCallback<Cluster>? onClusterTap;

  final double? maxDistance;

  final int? minClusterSize;

  final ClusterStyle? style;

  /// Creates a new [ClusterManager] object whose values are the same as this instance,
  /// unless overwritten by the specified parameters.
  ClusterManager copyWith({
    ArgumentCallback<Cluster>? onClusterTapParam,
    double? maxDistanceParam,
    int? minClusterSizeParam,
    ClusterStyle? styleParam,
  }) {
    return ClusterManager(
      clusterManagerId: clusterManagerId,
      onClusterTap: onClusterTapParam ?? onClusterTap,
      maxDistance: maxDistanceParam ?? maxDistance,
      minClusterSize: minClusterSizeParam ?? minClusterSize,
      style: styleParam ?? style,
    );
  }

  /// Creates a new [ClusterManager] object whose values are the same as this instance.
  @override
  ClusterManager clone() => copyWith();

  /// Converts this object to something serializable in JSON.
  @override
  Object toJson() {
    final json = <String, Object>{};

    void addIfPresent(String fieldName, Object? value) {
      if (value != null) {
        json[fieldName] = value;
      }
    }

    addIfPresent('clusterManagerId', clusterManagerId.value);
    addIfPresent('maxDistance', maxDistance);
    addIfPresent('minClusterSize', minClusterSize);
    if (style != null) {
      json['style'] = style!.toJson();
    }
    return json;
  }

  @override
  bool operator ==(Object other) {
    if (other.runtimeType != runtimeType) {
      return false;
    }
    return other is ClusterManager &&
        clusterManagerId == other.clusterManagerId &&
        maxDistance == other.maxDistance &&
        minClusterSize == other.minClusterSize &&
        style == other.style;
  }

  @override
  int get hashCode =>
      Object.hash(clusterManagerId, maxDistance, minClusterSize, style);

  @override
  String toString() {
    return 'Cluster{clusterManagerId: $clusterManagerId, onClusterTap: $onClusterTap, maxDistance: $maxDistance, minClusterSize: $minClusterSize, style: $style}';
  }
}
