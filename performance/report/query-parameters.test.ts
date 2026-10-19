import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { legacyConfiguration, queryUrl } from '../lib/query-parameters.ts';

const url = 'http://localhost:8080/assets/photo/-/entry/0/content';
const transformations = {
    width: 400,
    height: 300,
    quality: 80,
    gravity: 'center',
    rotate: 90,
    flip: 'h',
    'color-space': 'srgb',
    'pad-color': '#ffffff',
};

test('requests use descriptive names by default', () => {
    assert.equal(queryUrl(url, transformations), `${url}?width=400&height=300&quality=80&gravity=center&rotate=90&flip=h&color-space=srgb&pad-color=%23ffffff`);
});

test('legacy requests translate names while preserving values and the source parameters', () => {
    const parameters = { ...transformations };
    assert.equal(queryUrl(url, parameters, true), `${url}?w=400&h=300&q=80&g=center&r=90&f=h&cs=srgb&pad-c=%23ffffff`);
    assert.deepEqual(parameters, transformations);
});

test('both modes preserve other parameters and URL encoding', () => {
    const parameters = {
        format: 'pixels',
        'pixel-channels': 'RGB',
        'pixel-layout': 'planar',
        'label:width': 'wide & tall',
        profile: 'thumbnail',
        fit: 'fill',
        strip: 'exif,icc',
    };
    const expected = `${url}?format=pixels&pixel-channels=RGB&pixel-layout=planar&label%3Awidth=wide%20%26%20tall&profile=thumbnail&fit=fill&strip=exif%2Cicc`;
    for (const legacy of [false, true]) {
        assert.equal(queryUrl(url, parameters, legacy), expected);
        assert.equal(queryUrl(url, {}, legacy), url);
    }
});

test('legacy configuration translates assignment keys without changing values or comments', () => {
    assert.equal(legacyConfiguration(`profile {
    width = 400
    height = 300
    quality = 80
    gravity = center
    rotate = 90
    flip = h
    color-space = srgb
    pad-color = "#ffffff"
    pixel-channels = RGB
    pixel-layout = planar
    name = "width = 123"
    # width = 123
}`), `profile {
    w = 400
    h = 300
    q = 80
    g = center
    r = 90
    f = h
    cs = srgb
    pad-c = "#ffffff"
    pixel-channels = RGB
    pixel-layout = planar
    name = "width = 123"
    # width = 123
}`);
});

test('legacy runtime configuration covers eager profiles and upload preprocessing', async () => {
    const current = await readFile(new URL('../runtime/konifer.conf', import.meta.url), 'utf8');
    const legacy = legacyConfiguration(current);
    assert.equal((legacy.match(/^\s*w = /gm) ?? []).length, 3);
    assert.equal((legacy.match(/^\s*r = auto/gm) ?? []).length, 6);
    assert.doesNotMatch(legacy, /^\s*(width|rotate)\s*=/m);
    assert.match(current, /width = 200/);
    assert.match(legacy, /thumbnail-200 \{\s*w = 200\s*r = auto/);
    assert.match(legacy, /preprocessing \{\s*enabled = true\s*r = auto/);
});
