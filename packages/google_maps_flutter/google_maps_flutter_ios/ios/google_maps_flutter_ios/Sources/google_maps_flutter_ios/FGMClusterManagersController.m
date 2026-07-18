// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "FGMClusterManagersController.h"

#import <UIKit/UIKit.h>
#import "FGMConversionUtils.h"
#import "FGMMarkerUserData.h"

@interface FGMClusterIconGenerator : NSObject <GMUClusterIconGenerator>
- (instancetype)initWithStyle:(FGMPlatformClusterManager *)clusterStyle;
@end

@implementation FGMClusterIconGenerator {
  FGMPlatformClusterManager *_clusterStyle;
  NSMutableDictionary<NSNumber *, UIImage *> *_iconCache;
}

- (instancetype)initWithStyle:(FGMPlatformClusterManager *)clusterStyle {
  self = [super init];
  if (self) {
    _clusterStyle = clusterStyle;
    _iconCache = [[NSMutableDictionary alloc] init];
  }
  return self;
}

- (UIImage *)iconForSize:(NSUInteger)size {
  NSNumber *key = @(size);
  UIImage *cached = _iconCache[key];
  if (cached) {
    return cached;
  }

  CGFloat circleSizeVal = _clusterStyle.circleSize != nil ? _clusterStyle.circleSize.doubleValue : 44.0;
  circleSizeVal += 8.0;
  CGSize imageSize = CGSizeMake(circleSizeVal, circleSizeVal);
  UIGraphicsBeginImageContextWithOptions(imageSize, NO, 0.0);
  CGContextRef context = UIGraphicsGetCurrentContext();

  CGFloat radius = circleSizeVal / 2.0;

  CGContextSaveGState(context);
  uint32_t outerColorVal = _clusterStyle.outerRingColor != nil ? (uint32_t)_clusterStyle.outerRingColor.unsignedIntValue : 0x22007AFF;
  UIColor *outerColor = [UIColor colorWithRed:((outerColorVal >> 16) & 0xFF) / 255.0
                                        green:((outerColorVal >> 8) & 0xFF) / 255.0
                                         blue:(outerColorVal & 0xFF) / 255.0
                                        alpha:((outerColorVal >> 24) & 0xFF) / 255.0];
  [outerColor setFill];
  CGContextFillEllipseInRect(context, CGRectMake(0, 0, circleSizeVal, circleSizeVal));
  CGContextRestoreGState(context);

  CGContextSaveGState(context);
  CGFloat outerRingWidth = 8.0;
  CGFloat middleRadius = radius - outerRingWidth;
  if (middleRadius > 0) {
    uint32_t strokeColorVal = _clusterStyle.strokeColor != nil ? (uint32_t)_clusterStyle.strokeColor.unsignedIntValue : 0xFFFFFFFF;
    UIColor *strokeColor = [UIColor colorWithRed:((strokeColorVal >> 16) & 0xFF) / 255.0
                                           green:((strokeColorVal >> 8) & 0xFF) / 255.0
                                            blue:(strokeColorVal & 0xFF) / 255.0
                                           alpha:((strokeColorVal >> 24) & 0xFF) / 255.0];
    [strokeColor setFill];
    CGContextFillEllipseInRect(context, CGRectMake(radius - middleRadius, radius - middleRadius, middleRadius * 2.0, middleRadius * 2.0));
  }
  CGContextRestoreGState(context);

  CGContextSaveGState(context);
  CGFloat strokeWidth = 2.0;
  CGFloat innerRadius = middleRadius - strokeWidth;
  if (innerRadius > 0) {
    uint32_t coreColorVal = _clusterStyle.coreColor != nil ? (uint32_t)_clusterStyle.coreColor.unsignedIntValue : 0xFF007AFF;
    UIColor *coreColor = [UIColor colorWithRed:((coreColorVal >> 16) & 0xFF) / 255.0
                                         green:((coreColorVal >> 8) & 0xFF) / 255.0
                                          blue:(coreColorVal & 0xFF) / 255.0
                                         alpha:((coreColorVal >> 24) & 0xFF) / 255.0];
    [coreColor setFill];
    CGContextFillEllipseInRect(context, CGRectMake(radius - innerRadius, radius - innerRadius, innerRadius * 2.0, innerRadius * 2.0));
  }
  CGContextRestoreGState(context);

  NSString *text = [NSString stringWithFormat:@"%lu", (unsigned long)size];
  uint32_t textColorVal = _clusterStyle.textColor != nil ? (uint32_t)_clusterStyle.textColor.unsignedIntValue : 0xFFFFFFFF;
  UIColor *textColor = [UIColor colorWithRed:((textColorVal >> 16) & 0xFF) / 255.0
                                       green:((textColorVal >> 8) & 0xFF) / 255.0
                                        blue:(textColorVal & 0xFF) / 255.0
                                       alpha:((textColorVal >> 24) & 0xFF) / 255.0];
  CGFloat fontSizeVal = _clusterStyle.fontSize != nil ? _clusterStyle.fontSize.doubleValue : 14.0;
  
  UIFont *font = nil;
  if (_clusterStyle.fontFamily != nil) {
    font = [UIFont fontWithName:_clusterStyle.fontFamily size:fontSizeVal];
  }
  if (!font) {
    font = [UIFont boldSystemFontOfSize:fontSizeVal];
  }

  NSMutableParagraphStyle *paragraphStyle = [[NSMutableParagraphStyle alloc] init];
  paragraphStyle.alignment = NSTextAlignmentCenter;

  NSDictionary *attributes = @{
    NSFontAttributeName: font,
    NSForegroundColorAttributeName: textColor,
    NSParagraphStyleAttributeName: paragraphStyle
  };

  CGSize textSize = [text sizeWithAttributes:attributes];
  CGRect textRect = CGRectMake(0, radius - (textSize.height / 2.0), circleSizeVal, textSize.height);
  [text drawInRect:textRect withAttributes:attributes];

  UIImage *image = UIGraphicsGetImageFromCurrentImageContext();
  UIGraphicsEndImageContext();

  _iconCache[key] = image;
  return image;
}

@end

@interface FGMClusterManagersController () <GMUClusterRendererDelegate>

/// A dictionary mapping unique cluster manager identifiers to their corresponding cluster managers.
@property(strong, nonatomic)
    NSMutableDictionary<NSString *, GMUClusterManager *> *clusterManagerIdentifierToManagers;

@property(strong, nonatomic)
    NSMutableDictionary<NSString *, id<GMUClusterIconGenerator>> *clusterManagerIdentifierToIconGenerators;

/// The delegate for handling interactions with clusters.
@property(weak, nonatomic) NSObject<FGMMapEventDelegate> *eventDelegate;

/// The current map instance on which the cluster managers are operating.
@property(strong, nonatomic) GMSMapView *mapView;

@end

@implementation FGMClusterManagersController
- (instancetype)initWithMapView:(GMSMapView *)mapView
                  eventDelegate:(NSObject<FGMMapEventDelegate> *)eventDelegate {
  self = [super init];
  if (self) {
    _eventDelegate = eventDelegate;
    _mapView = mapView;
    _clusterManagerIdentifierToManagers = [[NSMutableDictionary alloc] init];
    _clusterManagerIdentifierToIconGenerators = [[NSMutableDictionary alloc] init];
  }
  return self;
}

- (void)addClusterManagers:(NSArray<FGMPlatformClusterManager *> *)clusterManagersToAdd {
  for (FGMPlatformClusterManager *clusterManager in clusterManagersToAdd) {
    [self addClusterManager:clusterManager];
  }
}

- (void)addClusterManager:(FGMPlatformClusterManager *)clusterManager {
  GMUClusterManager *existingManager = self.clusterManagerIdentifierToManagers[clusterManager.identifier];
  if (existingManager) {
    id<GMUClusterAlgorithm> algorithm;
    if (clusterManager.maxDistance != nil) {
      algorithm = [[GMUNonHierarchicalDistanceBasedAlgorithm alloc] initWithClusterDistancePoints:clusterManager.maxDistance.unsignedIntegerValue];
    } else {
      algorithm = [[GMUNonHierarchicalDistanceBasedAlgorithm alloc] init];
    }
    [existingManager setValue:algorithm forKey:@"algorithm"];
    
    id<GMUClusterIconGenerator> iconGenerator;
    if (clusterManager.coreColor != nil) {
      iconGenerator = [[FGMClusterIconGenerator alloc] initWithStyle:clusterManager];
    } else {
      iconGenerator = [[GMUDefaultClusterIconGenerator alloc] init];
    }
    self.clusterManagerIdentifierToIconGenerators[clusterManager.identifier] = iconGenerator;
    
    id<GMUClusterRenderer> renderer =
        [[GMUDefaultClusterRenderer alloc] initWithMapView:self.mapView
                                      clusterIconGenerator:iconGenerator];
    if ([renderer isKindOfClass:[GMUDefaultClusterRenderer class]]) {
      ((GMUDefaultClusterRenderer *)renderer).delegate = self;
      if (clusterManager.minClusterSize != nil) {
        ((GMUDefaultClusterRenderer *)renderer).minimumClusterSize = clusterManager.minClusterSize.unsignedIntegerValue;
      }
    }
    [existingManager setValue:renderer forKey:@"renderer"];
    [existingManager cluster];
    return;
  }

  id<GMUClusterAlgorithm> algorithm;
  if (clusterManager.maxDistance != nil) {
    algorithm = [[GMUNonHierarchicalDistanceBasedAlgorithm alloc] initWithClusterDistancePoints:clusterManager.maxDistance.unsignedIntegerValue];
  } else {
    algorithm = [[GMUNonHierarchicalDistanceBasedAlgorithm alloc] init];
  }
  id<GMUClusterIconGenerator> iconGenerator;
  if (clusterManager.coreColor != nil) {
    iconGenerator = [[FGMClusterIconGenerator alloc] initWithStyle:clusterManager];
  } else {
    iconGenerator = [[GMUDefaultClusterIconGenerator alloc] init];
  }
  self.clusterManagerIdentifierToIconGenerators[clusterManager.identifier] = iconGenerator;
  id<GMUClusterRenderer> renderer =
      [[GMUDefaultClusterRenderer alloc] initWithMapView:self.mapView
                                    clusterIconGenerator:iconGenerator];
  if ([renderer isKindOfClass:[GMUDefaultClusterRenderer class]]) {
    ((GMUDefaultClusterRenderer *)renderer).delegate = self;
    if (clusterManager.minClusterSize != nil) {
      ((GMUDefaultClusterRenderer *)renderer).minimumClusterSize = clusterManager.minClusterSize.unsignedIntegerValue;
    }
  }
  self.clusterManagerIdentifierToManagers[clusterManager.identifier] =
      [[GMUClusterManager alloc] initWithMap:self.mapView algorithm:algorithm renderer:renderer];
}

- (void)removeClusterManagersWithIdentifiers:(NSArray<NSString *> *)identifiers {
  for (NSString *identifier in identifiers) {
    GMUClusterManager *clusterManager =
        [self.clusterManagerIdentifierToManagers objectForKey:identifier];
    if (!clusterManager) {
      continue;
    }
    [clusterManager clearItems];
    [self.clusterManagerIdentifierToManagers removeObjectForKey:identifier];
    [self.clusterManagerIdentifierToIconGenerators removeObjectForKey:identifier];
  }
}

- (nullable GMUClusterManager *)clusterManagerWithIdentifier:(NSString *)identifier {
  return [self.clusterManagerIdentifierToManagers objectForKey:identifier];
}

- (void)invokeClusteringForEachClusterManager {
  for (GMUClusterManager *clusterManager in [self.clusterManagerIdentifierToManagers allValues]) {
    [clusterManager cluster];
  }
}

- (nullable NSArray<FGMPlatformCluster *> *)
    clustersWithIdentifier:(NSString *)identifier
                     error:(FlutterError *_Nullable __autoreleasing *_Nonnull)error {
  GMUClusterManager *clusterManager =
      [self.clusterManagerIdentifierToManagers objectForKey:identifier];

  if (!clusterManager) {
    *error = [FlutterError
        errorWithCode:@"Invalid clusterManagerId"
              message:@"getClusters called with invalid clusterManagerId"
              details:[NSString stringWithFormat:@"clusterManagerId was: '%@'", identifier]];
    return nil;
  }

  // Ref:
  // https://github.com/googlemaps/google-maps-ios-utils/blob/0e7ed81f1bbd9d29e4529c40ae39b0791b0a0eb8/src/Clustering/GMUClusterManager.m#L94.
  NSUInteger integralZoom = (NSUInteger)floorf(_mapView.camera.zoom + 0.5f);
  NSArray<id<GMUCluster>> *clusters = [clusterManager.algorithm clustersAtZoom:integralZoom];
  NSMutableArray<FGMPlatformCluster *> *response =
      [[NSMutableArray alloc] initWithCapacity:clusters.count];
  for (id<GMUCluster> cluster in clusters) {
    FGMPlatformCluster *platFormCluster = FGMGetPigeonCluster(cluster, identifier);
    [response addObject:platFormCluster];
  }
  return response;
}

- (void)didTapCluster:(GMUStaticCluster *)cluster {
  NSString *clusterManagerId = [self clusterManagerIdentifierForCluster:cluster];
  if (!clusterManagerId) {
    return;
  }
  FGMPlatformCluster *platFormCluster = FGMGetPigeonCluster(cluster, clusterManagerId);
  [self.eventDelegate didTapCluster:platFormCluster];
}

- (void)renderer:(id<GMUClusterRenderer>)renderer willRenderMarker:(GMSMarker *)marker {
  if ([marker.userData conformsToProtocol:@protocol(GMUCluster)]) {
    id<GMUCluster> cluster = (id<GMUCluster>)marker.userData;
    NSString *clusterManagerId = [self clusterManagerIdentifierForCluster:(GMUStaticCluster *)cluster];
    id<GMUClusterIconGenerator> iconGenerator = nil;
    if (clusterManagerId != nil) {
      iconGenerator = self.clusterManagerIdentifierToIconGenerators[clusterManagerId];
    }
    if (iconGenerator != nil) {
      NSUInteger count = 0;
      for (GMSMarker *item in cluster.items) {
        if ([item.userData isKindOfClass:[FGMMarkerUserData class]]) {
          count += ((FGMMarkerUserData *)item.userData).itemCount;
        } else {
          count += 1;
        }
      }
      marker.icon = [iconGenerator iconForSize:count];
    }
  }
}

#pragma mark - Private methods

/// Returns the cluster manager identifier for given cluster.
///
/// @return The cluster manager identifier if found; otherwise, nil.
- (nullable NSString *)clusterManagerIdentifierForCluster:(GMUStaticCluster *)cluster {
  if ([cluster.items.firstObject isKindOfClass:[GMSMarker class]]) {
    GMSMarker *firstMarker = (GMSMarker *)cluster.items.firstObject;
    return FGMGetClusterManagerIdentifierFromMarker(firstMarker);
  }

  return nil;
}

@end
