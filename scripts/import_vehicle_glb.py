"""Convert an AI-generated vehicle GLB into the game's compact mesh asset.

    python scripts/import_vehicle_glb.py model.glb app/src/main/assets/vehicles/<id> \
        --wheelbase 2.75 --front-axle-x 1.19 --cg 0.77 --wheel-radius 0.39 \
        --track 1.40 --wheel-width 0.27 [--flip] [--texture-size 1024]

What it does, in order:
  1. Reads positions, normals, UVs, indices and the base-colour texture out of
     the GLB (binary glTF 2.0, one or more triangle primitives).
  2. Finds the four tyre contact patches (the lowest vertices), which gives the
     model's axle positions, and scales/translates the whole mesh so those
     axles land on the physics axles: X along the wheelbase, tyres on the
     ground at y = -cg, centred in Z. Uniform scale, so proportions survive.
  3. Optionally flips the model end for end (--flip) when the generator put
     the nose at -X.
  4. Deletes the baked-in tyres and rims (triangles inside a cylinder around
     each physics wheel) so the game's own spinning, detachable wheels can be
     drawn there instead.
  5. Writes <out>.mesh (little-endian: magic CSM1, vertex count, index count,
     then x y z nx ny nz u v per vertex, then uint16 indices) and <out>.jpg,
     the albedo resized to --texture-size.

Body frame: X forward, Y up, Z right, centre of gravity at the origin.
"""
import argparse
import io
import json
import struct
import sys

import numpy as np
from PIL import Image

COMPONENT = {5120: ('b', 1), 5121: ('B', 1), 5122: ('h', 2), 5123: ('H', 2), 5125: ('I', 4), 5126: ('f', 4)}
NUM = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


def load_glb(path):
    data = open(path, 'rb').read()
    magic, _version, length = struct.unpack_from('<III', data, 0)
    assert magic == 0x46546C67, 'not a glb'
    off = 12
    gltf = None
    bin_chunk = None
    while off < length:
        clen, ctype = struct.unpack_from('<II', data, off)
        chunk = data[off + 8: off + 8 + clen]
        if ctype == 0x4E4F534A:
            gltf = json.loads(chunk.decode('utf-8'))
        elif ctype == 0x004E4942:
            bin_chunk = chunk
        off += 8 + clen
    return gltf, bin_chunk


def accessor(gltf, bin_chunk, idx):
    acc = gltf['accessors'][idx]
    bv = gltf['bufferViews'][acc['bufferView']]
    fmt, size = COMPONENT[acc['componentType']]
    n = NUM[acc['type']]
    count = acc['count']
    start = bv.get('byteOffset', 0) + acc.get('byteOffset', 0)
    stride = bv.get('byteStride', size * n)
    dtype = np.dtype(fmt)
    if stride == size * n:
        arr = np.frombuffer(bin_chunk, dtype=dtype, count=count * n, offset=start).reshape(count, n)
    else:
        raw = np.frombuffer(bin_chunk, dtype=np.uint8, count=stride * count, offset=start).reshape(count, stride)
        arr = raw[:, :size * n].copy().view(dtype).reshape(count, n)
    return arr.astype(np.float64) if fmt == 'f' else arr.astype(np.int64)


def node_world_matrices(gltf):
    mats = {}

    def local(node):
        if 'matrix' in node:
            return np.array(node['matrix'], dtype=np.float64).reshape(4, 4).T
        m = np.eye(4)
        t = node.get('translation', [0, 0, 0]); r = node.get('rotation', [0, 0, 0, 1]); s = node.get('scale', [1, 1, 1])
        x, y, z, w = r
        rot = np.array([
            [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
        ])
        m[:3, :3] = rot * np.array(s)[None, :]
        m[:3, 3] = t
        return m

    def walk(idx, parent):
        node = gltf['nodes'][idx]
        m = parent @ local(node)
        mats[idx] = m
        for c in node.get('children', []):
            walk(c, m)

    scene = gltf['scenes'][gltf.get('scene', 0)]
    for n in scene['nodes']:
        walk(n, np.eye(4))
    return mats


def gather(gltf, bin_chunk):
    """All triangle primitives merged: positions, normals, uvs (N,·) and faces (F,3), plus the albedo image."""
    mats = node_world_matrices(gltf)
    pos_all, nrm_all, uv_all, faces_all = [], [], [], []
    albedo = None
    base = 0
    for nidx, m in mats.items():
        node = gltf['nodes'][nidx]
        if 'mesh' not in node:
            continue
        for prim in gltf['meshes'][node['mesh']]['primitives']:
            if prim.get('mode', 4) != 4:
                continue
            pos = accessor(gltf, bin_chunk, prim['attributes']['POSITION'])
            pos = (m[:3, :3] @ pos.T).T + m[:3, 3]
            nrm_mat = np.linalg.inv(m[:3, :3]).T
            if 'NORMAL' in prim['attributes']:
                nrm = accessor(gltf, bin_chunk, prim['attributes']['NORMAL'])
                nrm = (nrm_mat @ nrm.T).T
            else:
                nrm = np.zeros_like(pos)
            uv = accessor(gltf, bin_chunk, prim['attributes']['TEXCOORD_0']) if 'TEXCOORD_0' in prim['attributes'] else np.zeros((len(pos), 2))
            idx = accessor(gltf, bin_chunk, prim['indices']).reshape(-1) if 'indices' in prim else np.arange(len(pos))
            faces_all.append(idx.reshape(-1, 3) + base)
            pos_all.append(pos); nrm_all.append(nrm); uv_all.append(uv[:, :2])
            base += len(pos)
            if albedo is None and 'material' in prim:
                pbr = gltf['materials'][prim['material']].get('pbrMetallicRoughness', {})
                if 'baseColorTexture' in pbr:
                    src = gltf['textures'][pbr['baseColorTexture']['index']]['source']
                    bv = gltf['bufferViews'][gltf['images'][src]['bufferView']]
                    start = bv.get('byteOffset', 0)
                    albedo = Image.open(io.BytesIO(bin_chunk[start:start + bv['byteLength']])).convert('RGB')
    return np.concatenate(pos_all), np.concatenate(nrm_all), np.concatenate(uv_all), np.concatenate(faces_all), albedo


def find_axles(pos):
    """X positions of the front and rear tyre contact patches, from the lowest vertices."""
    y_min = pos[:, 1].min()
    height = pos[:, 1].max() - y_min
    low = pos[pos[:, 1] < y_min + height * 0.03]
    xs = low[:, 0]
    # Two clusters along X: split at the widest gap in the sorted values.
    order = np.sort(xs)
    gaps = np.diff(order)
    split = order[np.argmax(gaps)] + gaps.max() / 2
    a = xs[xs < split].mean(); b = xs[xs >= split].mean()
    return min(a, b), max(a, b), y_min


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('glb'); ap.add_argument('out')
    ap.add_argument('--wheelbase', type=float, required=True)
    ap.add_argument('--front-axle-x', type=float, required=True)
    ap.add_argument('--cg', type=float, required=True, help='centre of gravity height above the road at rest')
    ap.add_argument('--wheel-radius', type=float, required=True)
    ap.add_argument('--track', type=float, required=True)
    ap.add_argument('--wheel-width', type=float, required=True)
    ap.add_argument('--flip', action='store_true', help='model nose is at -X; turn it around')
    ap.add_argument('--texture-size', type=int, default=1024)
    ap.add_argument('--keep-wheels', action='store_true')
    args = ap.parse_args()

    gltf, bin_chunk = load_glb(args.glb)
    pos, nrm, uv, faces, albedo = gather(gltf, bin_chunk)
    print(f'read {len(pos)} vertices, {len(faces)} triangles, albedo {albedo.size if albedo else None}')

    if args.flip:
        pos = pos * np.array([-1.0, 1.0, -1.0])
        nrm = nrm * np.array([-1.0, 1.0, -1.0])

    x_rear, x_front, y_ground = find_axles(pos)
    scale = args.wheelbase / (x_front - x_rear)
    rear_axle_x = args.front_axle_x - args.wheelbase
    z_centre = (pos[:, 2].min() + pos[:, 2].max()) / 2
    pos = (pos - np.array([x_rear, y_ground, z_centre])) * scale + np.array([rear_axle_x, -args.cg, 0.0])
    ext = pos.max(axis=0) - pos.min(axis=0)
    print(f'model axles at x={x_rear:.3f}/{x_front:.3f}, scale {scale:.4f}; aligned size {np.round(ext, 3)} '
          f'(length {ext[0]:.2f} m, width {ext[2]:.2f} m, height {ext[1]:.2f} m)')

    if not args.keep_wheels:
        half_track = args.track / 2
        z_in = min(half_track - args.wheel_width / 2 - 0.12, 0.30)
        c = pos[faces].mean(axis=1)
        axles = (rear_axle_x, args.front_axle_x)
        # The generator draws its own idea of the tyre size, so measure it: the
        # outermost vertices near an axle are the tyre's outer sidewall and their
        # height range is its diameter. A bumper or flare can share that plane at
        # one axle and inflate the number, so take the smaller of the two axles;
        # all four tyres are the same size and they all touch the ground.
        radii = []
        for ax in axles:
            near = pos[(np.abs(pos[:, 0] - ax) < args.wheel_radius * 0.6) & (np.abs(pos[:, 2]) > z_in)]
            outer = near[np.abs(near[:, 2]) > np.abs(near[:, 2]).max() - 0.03]
            radii.append((outer[:, 1].max() - outer[:, 1].min()) / 2)
        r_model = min(radii)
        if not (args.wheel_radius * 0.7 < r_model < args.wheel_radius * 1.6):
            sys.exit(f'measured tyre radius {r_model:.3f} m is implausible; check the model')
        centre_y = -args.cg + r_model
        print(f'model tyre radius {r_model:.3f} m (spec {args.wheel_radius}), per axle {np.round(radii, 3)}')
        keep = np.ones(len(faces), dtype=bool)
        for ax in axles:
            in_disc = (c[:, 0] - ax) ** 2 + (c[:, 1] - centre_y) ** 2 < (r_model * 1.06) ** 2
            keep &= ~(in_disc & (np.abs(c[:, 2]) > z_in))
        print(f'cut {int((~keep).sum())} wheel triangles')
        faces = faces[keep]

    # Drop now-unreferenced vertices and renumber.
    used = np.unique(faces)
    remap = -np.ones(len(pos), dtype=np.int64); remap[used] = np.arange(len(used))
    pos, nrm, uv, faces = pos[used], nrm[used], uv[used], remap[faces]
    if len(pos) > 65535:
        sys.exit(f'{len(pos)} vertices exceed the 16-bit index limit; regenerate with a lower face limit')

    # Normals: normalise, fill any zero ones from face normals.
    ln = np.linalg.norm(nrm, axis=1)
    bad = ln < 1e-9
    if bad.any():
        fn = np.cross(pos[faces[:, 1]] - pos[faces[:, 0]], pos[faces[:, 2]] - pos[faces[:, 0]])
        acc = np.zeros_like(pos)
        for k in range(3):
            np.add.at(acc, faces[:, k], fn)
        nrm[bad] = acc[bad]
        ln = np.linalg.norm(nrm, axis=1)
    nrm = nrm / np.maximum(ln, 1e-12)[:, None]

    verts = np.hstack([pos, nrm, uv]).astype('<f4')
    with open(args.out + '.mesh', 'wb') as f:
        f.write(b'CSM1')
        f.write(struct.pack('<II', len(pos), faces.size))
        f.write(verts.tobytes())
        f.write(faces.astype('<u2').tobytes())
    if albedo is not None:
        albedo.resize((args.texture_size, args.texture_size), Image.LANCZOS).save(args.out + '.jpg', quality=90)
    print(f'wrote {args.out}.mesh ({len(pos)} vertices, {len(faces)} triangles) and {args.out}.jpg')


if __name__ == '__main__':
    main()
