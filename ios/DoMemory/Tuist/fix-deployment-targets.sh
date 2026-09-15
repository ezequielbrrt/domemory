#!/bin/sh
# Normalizes IPHONEOS_DEPLOYMENT_TARGET to 18.6 across every generated
# third-party package project, unconditionally. Two things make this the
# actual fix rather than Tuist/Package.swift's `PackageSettings.baseSettings`
# alone (kept there as a defensive fallback, not sufficient by itself):
#
# 1. Coverage: `baseSettings` only lands on each generated project's
#    *project-level* default config. Tuist also writes an explicit
#    IPHONEOS_DEPLOYMENT_TARGET directly onto most individual targets, which
#    wins over that default, and cannot write one at all onto the
#    resource-bundle targets (`com.apple.product-type.bundle`) it
#    auto-synthesizes for a package's `resources:` declaration — those
#    aren't addressable through `PackageSettings.targetSettings` by any name
#    (verified directly: listing their exact generated names there has no
#    effect).
# 2. Uniformity: this has to touch *every* target, not just the ones
#    originally below Xcode's iOS 27 SDK 15.0 floor. Once a dependency like
#    Promises is raised to 18.6, Swift's cross-module deployment-target
#    check then refuses to let a target still sitting at a lower value
#    (e.g. Firebase's own targets, which were already a valid 15.0) import
#    it — "compiling for iOS 15.0, but module 'Promises' has a minimum
#    deployment target of iOS 18.6". Raising only the targets that were
#    failing outright just moves the failure.
#
# Run this immediately after `tuist generate`/`tuist install`, before any
# xcodebuild invocation — unlike a scheme pre-action (tried previously, see
# git history), a build's own deployment-target validation runs before any
# pre-action script executes, so patching from inside the build is always
# too late. Patching the files on disk beforehand, here, actually works.
set -eu
cd "$(dirname "$0")/.build/tuist-derived/Projects" 2>/dev/null || exit 0
find . -name project.pbxproj -exec sed -i '' -E \
  's/IPHONEOS_DEPLOYMENT_TARGET = [0-9]+(\.[0-9]+)?;/IPHONEOS_DEPLOYMENT_TARGET = 18.6;/g' {} +
