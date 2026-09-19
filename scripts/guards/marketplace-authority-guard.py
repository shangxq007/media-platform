#!/usr/bin/env python3
"""Bounded Marketplace ownership check using the existing candidate filesystem census."""
from pathlib import Path
import argparse,re,tempfile
from h7_input_boundary import candidate_files

RETIRED = {
 'com.example.platform.shared.events.AssetApprovedEvent',
 'com.example.platform.shared.events.AssetArchivedEvent',
 'com.example.platform.shared.events.AssetPublishedEvent',
 'com.example.platform.shared.events.AssetSubmittedForReviewEvent',
 'com.example.platform.render.app.asset.AssetReviewService',
 'com.example.platform.render.app.asset.MarketplaceConsumer',
 'com.example.platform.render.app.asset.MarketplaceListingBuilder',
 'com.example.platform.render.app.asset.MarketplacePackageTaskHandler',
 'com.example.platform.render.app.asset.MarketplaceValidateTaskHandler',
 'com.example.platform.render.app.event.AssetPublicationEventPublisher',
 'com.example.platform.render.infrastructure.asset.MarketplaceListingRepository',
 'com.example.platform.render.infrastructure.productization.marketplace.Marketplace',
 'com.example.platform.render.infrastructure.productization.marketplace.MarketplaceService',
 'com.example.platform.render.domain.asset.marketplace.MarketplaceListing',
 'com.example.platform.render.domain.asset.marketplace.MarketplaceListingType',
 'com.example.platform.render.domain.asset.marketplace.MarketplaceListingStatus',
}

def violations(root):
    errors=[]
    settings=(root/'settings.gradle.kts').read_text()
    app=(root/'platform-app/src/main/java/com/example/platform/PlatformApplication.java').read_text()
    if '"marketplace-module"' not in settings:errors.append('MARKETPLACE_PROJECT_MISSING')
    if 'MarketplaceConfiguration.class' not in app or '"com.example.platform.marketplace"' in app:errors.append('MARKETPLACE_TARGETED_COMPOSITION_MISSING')
    if 'project(":marketplace-module")' not in (root/'platform-app/build.gradle.kts').read_text():errors.append('MARKETPLACE_APP_DEPENDENCY_MISSING')
    for path in candidate_files(root,'*.java'):
        rel=path.relative_to(root).as_posix()
        if '/src/main/java/' not in rel or rel.startswith('typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated/'):continue
        text=path.read_text();package=re.search(r'\bpackage\s+([\w.]+)\s*;',text)
        fqcn=(package.group(1)+'.'+path.stem) if package else ''
        if fqcn in RETIRED or any(name in text for name in RETIRED):errors.append('RETIRED_MARKETPLACE_AUTHORITY:'+rel)
        if not rel.startswith('marketplace-module/') and re.search(r'(?i)\b(?:from|into|update|join)\s+marketplace_(?:listing|review|command)\b|\bMARKETPLACE_LISTING\.',text):errors.append('FOREIGN_MARKETPLACE_PERSISTENCE:'+rel)
        if rel.startswith('marketplace-module/') and re.search(r'\bimport\s+com\.example\.platform\.render\.',text):errors.append('MARKETPLACE_RENDER_DEPENDENCY:'+rel)
        if rel.startswith('timeline-module/') and 'createAssetReview(' in text:errors.append('TIMELINE_MARKETPLACE_BRIDGE:'+rel)
    return errors

def self_test():
    with tempfile.TemporaryDirectory(prefix='marketplace-guard-') as temp:
        r=Path(temp)
        def put(path,text):
            p=r/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);return p
        put('settings.gradle.kts','include("marketplace-module")')
        put('platform-app/build.gradle.kts','implementation(project(":marketplace-module"))')
        put('platform-app/src/main/java/com/example/platform/PlatformApplication.java','@Import(MarketplaceConfiguration.class) class PlatformApplication {}')
        assert not violations(r)
        cases=[('render-module/src/main/java/Bad.java','package com.example.platform.render.app.asset; class AssetReviewService {}'),
               ('platform-app/src/main/java/Bad.java','class Bad { String sql="update marketplace_listing set status=1"; }'),
               ('marketplace-module/src/main/java/Bad.java','import com.example.platform.render.infrastructure.asset.Anything; class Bad {}'),
               ('timeline-module/src/main/java/Bad.java','class Bad { void createAssetReview() {} }'),
               ('render-module/src/main/java/.worktrees/Bad.java','class Bad { String target="com.example.platform.render.infrastructure.productization.marketplace.MarketplaceService"; }')]
        for path,source in cases:
            # A declared retired type is recognized by its actual Java identity.
            if 'class AssetReviewService' in source:path=path.replace('Bad.java','AssetReviewService.java')
            p=put(path,source);assert violations(r),path;p.unlink()
        put('.worktrees/protected/render-module/src/main/java/AssetReviewService.java','package com.example.platform.render.app.asset; class AssetReviewService {}')
        assert not violations(r)
        print('MARKETPLACE_GUARD_CONTROLS=7 PASS')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);parser.add_argument('--self-test',action='store_true');args=parser.parse_args()
    if args.self_test:self_test()
    errors=violations(args.root)
    for error in errors:print(error)
    print('MARKETPLACE_AUTHORITY_GUARD='+('FAIL' if errors else 'PASS'))
    raise SystemExit(bool(errors))
