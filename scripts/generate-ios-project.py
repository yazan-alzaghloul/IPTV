#!/usr/bin/env python3
"""Generate the checked-in Xcode project using only Python's standard library."""
from pathlib import Path
import hashlib, json
root = Path(__file__).resolve().parents[1] / 'apps/ios'
objects = {}
def ident(name): return hashlib.sha1(name.encode()).hexdigest()[:24].upper()
def add(keyname, isa, **fields):
    key = ident(keyname); objects[key] = dict(isa=isa, **fields); return key
def q(value, level=0):
    indent = '\t' * level
    if isinstance(value, dict):
        return '{\n' + ''.join(indent + '\t' + json.dumps(k) + ' = ' + q(v, level + 1) + ';\n' for k, v in value.items()) + indent + '}'
    if isinstance(value, list):
        return '(\n' + ''.join(indent + '\t' + q(v, level + 1) + ',\n' for v in value) + indent + ')'
    if isinstance(value, int): return str(value)
    if value in objects or (len(value) == 24 and all(c in '0123456789ABCDEF' for c in value)): return value
    return json.dumps(value)

products = []
groups = []
source_ids = {}
for directory in ['App', 'Features', 'AppTests', 'UITests']:
    refs = []
    for path in sorted((root / directory).glob('*.swift')):
        ref = add(str(path.relative_to(root)), 'PBXFileReference', lastKnownFileType='sourcecode.swift', path=path.name, sourceTree='<group>')
        refs.append(ref); source_ids[str(path.relative_to(root))] = ref
    groups.append(add('group'+directory, 'PBXGroup', children=refs, path=directory, sourceTree='<group>'))
resource_refs = []
for name, kind in [('Assets.xcassets', 'folder.assetcatalog'), ('PrivacyInfo.xcprivacy', 'text.xml')]:
    resource_refs.append(add('resource'+name, 'PBXFileReference', lastKnownFileType=kind, path=name, sourceTree='<group>'))
for filename in ['Localizable.strings', 'InfoPlist.strings']:
    refs = [add(filename+language, 'PBXFileReference', lastKnownFileType='text.plist.strings', name=language, path=f'{language}.lproj/{filename}', sourceTree='<group>') for language in ['en', 'ar']]
    resource_refs.append(add(filename, 'PBXVariantGroup', children=refs, name=filename, sourceTree='<group>'))
groups.append(add('groupResources', 'PBXGroup', children=resource_refs, path='Resources', sourceTree='<group>'))
package = add('package', 'XCLocalSwiftPackageReference', relativePath='Core')
project_key = ident('project')
targets = []
for name, directories, product_type in [('IPTV', ['App', 'Features'], 'application'), ('IPTVTests', ['AppTests'], 'bundle.unit-test'), ('IPTVUITests', ['UITests'], 'bundle.ui-testing')]:
    ext = 'app' if name == 'IPTV' else 'xctest'
    product = add(name+'product', 'PBXFileReference', explicitFileType='wrapper.application' if name == 'IPTV' else 'wrapper.cfbundle', includeInIndex=0, path=f'{name}.{ext}', sourceTree='BUILT_PRODUCTS_DIR'); products.append(product)
    builds = [add(name+path+'build', 'PBXBuildFile', fileRef=ref) for path, ref in source_ids.items() if path.split('/')[0] in directories]
    sources = add(name+'sources', 'PBXSourcesBuildPhase', buildActionMask=2147483647, files=builds, runOnlyForDeploymentPostprocessing=0)
    frameworks_files, deps = [], []
    if name == 'IPTV':
        pkgprod = add(name+'packageproduct', 'XCSwiftPackageProductDependency', productName='IPTVCore', package=package)
        deps.append(pkgprod); frameworks_files.append(add(name+'packagebuild', 'PBXBuildFile', productRef=pkgprod))
    frameworks = add(name+'frameworks', 'PBXFrameworksBuildPhase', buildActionMask=2147483647, files=frameworks_files, runOnlyForDeploymentPostprocessing=0)
    resources = add(name+'resources', 'PBXResourcesBuildPhase', buildActionMask=2147483647, files=[add(name+r+'build', 'PBXBuildFile', fileRef=r) for r in resource_refs] if name == 'IPTV' else [], runOnlyForDeploymentPostprocessing=0)
    configurations = []
    for config in ['Debug', 'Release']:
        settings = {'PRODUCT_BUNDLE_IDENTIFIER': 'com.yazanalzaghloul.iptv' + ('' if name == 'IPTV' else '.'+name), 'PRODUCT_NAME': '$(TARGET_NAME)', 'SWIFT_VERSION': '5.0', 'IPHONEOS_DEPLOYMENT_TARGET': '17.0', 'TARGETED_DEVICE_FAMILY': '1,2', 'SDKROOT': 'iphoneos', 'SUPPORTED_PLATFORMS': 'iphoneos iphonesimulator', 'CODE_SIGN_STYLE': 'Automatic', 'SWIFT_EMIT_LOC_STRINGS': 'YES', 'ENABLE_USER_SCRIPT_SANDBOXING': 'YES', 'ENABLE_DEBUG_DYLIB': 'NO', 'SWIFT_STRICT_CONCURRENCY': 'targeted'}
        if name == 'IPTV': settings.update(INFOPLIST_FILE='Resources/Info.plist', ASSETCATALOG_COMPILER_APPICON_NAME='AppIcon', CURRENT_PROJECT_VERSION='4', MARKETING_VERSION='1.3.0', GENERATE_INFOPLIST_FILE='NO', LD_RUNPATH_SEARCH_PATHS=['$(inherited)', '@executable_path/Frameworks'])
        else: settings.update(GENERATE_INFOPLIST_FILE='YES', TEST_TARGET_NAME='IPTV')
        if name == 'IPTVTests': settings.update(TEST_HOST='$(BUILT_PRODUCTS_DIR)/IPTV.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/IPTV', BUNDLE_LOADER='$(TEST_HOST)')
        if config == 'Debug': settings.update(SWIFT_OPTIMIZATION_LEVEL='-Onone', SWIFT_ACTIVE_COMPILATION_CONDITIONS='DEBUG $(inherited)', ENABLE_TESTABILITY='YES')
        else: settings.update(SWIFT_COMPILATION_MODE='wholemodule', SWIFT_OPTIMIZATION_LEVEL='-O')
        configurations.append(add(name+config, 'XCBuildConfiguration', buildSettings=settings, name=config))
    configlist = add(name+'configlist', 'XCConfigurationList', buildConfigurations=configurations, defaultConfigurationIsVisible=0, defaultConfigurationName='Release')
    dependencies = []
    if name != 'IPTV':
        proxy = add(name+'proxy', 'PBXContainerItemProxy', containerPortal=project_key, proxyType=1, remoteGlobalIDString=ident('IPTVtarget'), remoteInfo='IPTV')
        dependencies.append(add(name+'dependency', 'PBXTargetDependency', target=ident('IPTVtarget'), targetProxy=proxy))
    target = add(name+'target', 'PBXNativeTarget', buildConfigurationList=configlist, buildPhases=[sources, frameworks, resources], buildRules=[], dependencies=dependencies, name=name, packageProductDependencies=deps, productName=name, productReference=product, productType='com.apple.product-type.'+product_type)
    targets.append(target)
productgroup = add('products', 'PBXGroup', children=products, name='Products', sourceTree='<group>')
main = add('main', 'PBXGroup', children=groups + [productgroup], sourceTree='<group>')
configs=[]
for config in ['Debug','Release']:
    configs.append(add('project'+config,'XCBuildConfiguration', buildSettings={'CLANG_ENABLE_MODULES':'YES','CLANG_ENABLE_OBJC_ARC':'YES','SWIFT_VERSION':'5.0','IPHONEOS_DEPLOYMENT_TARGET':'17.0','SDKROOT':'iphoneos','DEBUG_INFORMATION_FORMAT':'dwarf' if config=='Debug' else 'dwarf-with-dsym','GCC_WARN_INHIBIT_ALL_WARNINGS':'NO'},name=config))
projectconfigs=add('projectconfigs','XCConfigurationList',buildConfigurations=configs,defaultConfigurationIsVisible=0,defaultConfigurationName='Release')
add('project','PBXProject',attributes={'BuildIndependentTargetsInParallel':'YES','LastUpgradeCheck':'1600'},buildConfigurationList=projectconfigs,compatibilityVersion='Xcode 15.0',developmentRegion='en',hasScannedForEncodings=0,knownRegions=['en','ar','Base'],mainGroup=main,packageReferences=[package],productRefGroup=productgroup,projectDirPath='',projectRoot='',targets=targets)
project = root / 'IPTV.xcodeproj'; project.mkdir(exist_ok=True)
(project/'project.pbxproj').write_text('// !$*UTF8*$!\n'+q({'archiveVersion':1,'classes':{},'objectVersion':60,'objects':objects,'rootObject':project_key})+'\n')
refs = ''.join(f'<TestableReference skipped="NO"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{ident(n+"target")}" BuildableName="{n}.xctest" BlueprintName="{n}" ReferencedContainer="container:IPTV.xcodeproj"/></TestableReference>' for n in ['IPTVTests','IPTVUITests'])
appref=f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{ident("IPTVtarget")}" BuildableName="IPTV.app" BlueprintName="IPTV" ReferencedContainer="container:IPTV.xcodeproj"/>'
scheme=f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3"><BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{appref}</BuildActionEntry></BuildActionEntries></BuildAction><TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables>{refs}</Testables></TestAction><LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{appref}</BuildableProductRunnable></LaunchAction><ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugServiceExtension="internal"><BuildableProductRunnable runnableDebuggingMode="0">{appref}</BuildableProductRunnable></ProfileAction><AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/></Scheme>'''
(project/'xcshareddata/xcschemes/IPTV.xcscheme').write_text(scheme)
