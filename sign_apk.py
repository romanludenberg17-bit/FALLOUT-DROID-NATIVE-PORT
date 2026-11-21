import base64, hashlib, os, struct, subprocess, sys, tempfile, zipfile, zlib

STORE_EXTS = {'.arsc', '.mp4', '.mp3', '.ogg', '.wav', '.jpg', '.jpeg', '.png', '.webp'}
SIG_SKIP = {'META-INF/MANIFEST.MF', 'META-INF/CERT.SF', 'META-INF/CERT.RSA'}

def is_sig_file(rel_path):
    if rel_path in SIG_SKIP:
        return True
    if rel_path.startswith('META-INF/'):
        upper = rel_path.upper()
        if upper.endswith('.SF') or upper.endswith('.RSA') or upper.endswith('.DSA') or upper.endswith('.EC') or upper.endswith('MANIFEST.MF'):
            return True
    return False

def should_store(rel_path):
    if rel_path == 'resources.arsc':
        return True
    if rel_path.startswith('assets/linuxfs/'):
        return False
    _, ext = os.path.splitext(rel_path.lower())
    return ext in STORE_EXTS

def ensure_keys(key_dir):
    os.makedirs(key_dir, exist_ok=True)
    priv_pem = os.path.join(key_dir, 'release.key.pem')
    cert_pem = os.path.join(key_dir, 'release.cert.pem')
    cert_der = os.path.join(key_dir, 'release.cert.der')
    if not (os.path.exists(priv_pem) and os.path.exists(cert_pem) and os.path.exists(cert_der)):
        subprocess.run([
            'openssl', 'req', '-new', '-x509', '-nodes', '-newkey', 'rsa:2048',
            '-keyout', priv_pem, '-out', cert_pem, '-days', '10000',
            '-subj', '/CN=FalloutDroid Release/O=WastelandDroid/C=US'
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run([
            'openssl', 'x509', '-in', cert_pem, '-outform', 'DER', '-out', cert_der
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return priv_pem, cert_pem, cert_der

def wrap70(text):
    out = []
    for line in text.splitlines():
        b = line.encode('utf-8')
        if len(b) <= 70:
            out.append(line)
        else:
            out.append(b[:70].decode('utf-8'))
            b = b[70:]
            while b:
                out.append(' ' + b[:69].decode('utf-8'))
                b = b[69:]
    return '\r\n'.join(out) + '\r\n'

def compute_v2_merkle_root(chunks):
    level = []
    for c in chunks:
        h = hashlib.sha256()
        h.update(b'\xa5')
        h.update(struct.pack('<I', len(c)))
        h.update(c)
        level.append(h.digest())
    h = hashlib.sha256()
    h.update(b'\x5a')
    h.update(struct.pack('<I', len(level)))
    for d in level:
        h.update(d)
    return h.digest()

def lp_bytes(data):
    return struct.pack('<I', len(data)) + data

def build_and_sign_apk(src_dir, out_apk):
    key_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), '.signing_keys')
    priv_pem, cert_pem, cert_der = ensure_keys(key_dir)

    files = []
    for root, _, fnames in os.walk(src_dir):
        if '.git' in root.split(os.sep):
            continue
        for fn in fnames:
            full = os.path.join(root, fn)
            rel = os.path.relpath(full, src_dir).replace(os.sep, '/')
            if rel.startswith('.git/') or is_sig_file(rel):
                continue
            files.append((rel, full))
    files.sort(key=lambda x: x[0])

    # Build v1 MANIFEST.MF, CERT.SF, CERT.RSA
    mf_sections = ['Manifest-Version: 1.0\r\nCreated-By: FalloutDroid Release Builder\r\n\r\n']
    for rel, full in files:
        with open(full, 'rb') as f:
            digest = base64.b64encode(hashlib.sha256(f.read()).digest()).decode('ascii')
        sec = wrap70(f'Name: {rel}\r\nSHA-256-Digest: {digest}') + '\r\n'
        mf_sections.append(sec)
    manifest_bytes = ''.join(mf_sections).encode('utf-8')

    main_mf_digest = base64.b64encode(hashlib.sha256(manifest_bytes).digest()).decode('ascii')
    sf_sections = [
        f'Signature-Version: 1.0\r\nCreated-By: FalloutDroid Release Builder\r\nSHA-256-Digest-Manifest: {main_mf_digest}\r\nX-Android-APK-Signed: 2\r\n\r\n'
    ]
    for sec in mf_sections[1:]:
        name_line = sec.splitlines()[0]
        sec_digest = base64.b64encode(hashlib.sha256(sec.encode('utf-8')).digest()).decode('ascii')
        sf_sections.append(wrap70(f'{name_line}\r\nSHA-256-Digest: {sec_digest}') + '\r\n')
    sf_bytes = ''.join(sf_sections).encode('utf-8')

    with tempfile.NamedTemporaryFile(delete=False) as tf_sf, tempfile.NamedTemporaryFile(delete=False) as tf_rsa:
        tf_sf.write(sf_bytes)
        tf_sf_name = tf_sf.name
        tf_rsa_name = tf_rsa.name
    subprocess.run([
        'openssl', 'cms', '-sign', '-binary', '-noattr', '-in', tf_sf_name,
        '-signer', cert_pem, '-inkey', priv_pem, '-outform', 'DER', '-out', tf_rsa_name
    ], check=True)
    with open(tf_rsa_name, 'rb') as f:
        rsa_bytes = f.read()
    os.unlink(tf_sf_name)
    os.unlink(tf_rsa_name)

    # Write ZIP with 4-byte alignment on all STORED entries
    entries = [
        ('META-INF/MANIFEST.MF', manifest_bytes, False),
        ('META-INF/CERT.SF', sf_bytes, False),
        ('META-INF/CERT.RSA', rsa_bytes, False),
    ]
    for rel, full in files:
        entries.append((rel, full, should_store(rel)))

    tmp_zip = out_apk + '.unsigned.tmp'
    with open(tmp_zip, 'wb') as out_f:
        cd_records = []
        for name, src, store in entries:
            name_b = name.encode('utf-8')
            if isinstance(src, bytes):
                raw_data = src
            else:
                with open(src, 'rb') as rf:
                    raw_data = rf.read()
            crc = zlib.crc32(raw_data) & 0xffffffff
            uncomp_size = len(raw_data)
            if store:
                comp_type = 0
                comp_data = raw_data
            else:
                comp_type = 8
                co = zlib.compressobj(6, zlib.DEFLATED, -15)
                comp_data = co.compress(raw_data) + co.flush()
            comp_size = len(comp_data)
            local_hdr_off = out_f.tell()
            extra = b''
            if store:
                data_start = local_hdr_off + 30 + len(name_b)
                pad = (4 - (data_start % 4)) % 4
                extra = b'\x00' * pad
            lfh = struct.pack(
                '<4sHHHHHIIIHH',
                b'PK\x03\x04', 20, 0, comp_type, 0, 0x2100,
                crc, comp_size, uncomp_size, len(name_b), len(extra)
            )
            out_f.write(lfh)
            out_f.write(name_b)
            out_f.write(extra)
            out_f.write(comp_data)

            cdh = struct.pack(
                '<4sHHHHHHIIIHHHHHII',
                b'PK\x01\x02', 20, 20, 0, comp_type, 0, 0x2100,
                crc, comp_size, uncomp_size, len(name_b), 0, 0, 0, 0, 0, local_hdr_off
            ) + name_b
            cd_records.append(cdh)

        cd_offset = out_f.tell()
        cd_bytes = b''.join(cd_records)
        out_f.write(cd_bytes)
        eocd = struct.pack(
            '<4sHHHHIIH',
            b'PK\x05\x06', 0, 0, len(cd_records), len(cd_records),
            len(cd_bytes), cd_offset, 0
        )
        out_f.write(eocd)

    with open(tmp_zip, 'rb') as f:
        zip_data = f.read()
    os.unlink(tmp_zip)

    before_cd = zip_data[:cd_offset]
    cd_section = zip_data[cd_offset:cd_offset+len(cd_bytes)]
    eocd_section = bytearray(zip_data[cd_offset+len(cd_bytes):])

    # Build APK Signature Scheme v2 Block (exact AOSP ApkSignatureSchemeV2Verifier format)
    chunks = []
    for section in (before_cd, cd_section, bytes(eocd_section)):
        for i in range(0, len(section), 1048576):
            chunks.append(section[i:i+1048576])
    top_digest = compute_v2_merkle_root(chunks)

    alg_id = 0x0103
    digest_entry = lp_bytes(struct.pack('<I', alg_id) + lp_bytes(top_digest))
    digests_seq = lp_bytes(digest_entry)

    with open(cert_der, 'rb') as f:
        cert_der_bytes = f.read()
    certs_seq = lp_bytes(lp_bytes(cert_der_bytes))
    attrs_seq = lp_bytes(b'')

    # digests_seq is ALREADY length-prefixed; do NOT wrap it in lp_bytes a second time!
    signed_data = digests_seq + certs_seq + attrs_seq

    with tempfile.NamedTemporaryFile(delete=False) as tf_sd, tempfile.NamedTemporaryFile(delete=False) as tf_sig:
        tf_sd.write(signed_data)
        tf_sd_name = tf_sd.name
        tf_sig_name = tf_sig.name
    subprocess.run([
        'openssl', 'dgst', '-sha256', '-sign', priv_pem, '-out', tf_sig_name, tf_sd_name
    ], check=True)
    with open(tf_sig_name, 'rb') as f:
        sig_bytes = f.read()
    os.unlink(tf_sd_name)
    os.unlink(tf_sig_name)

    pub_der = subprocess.check_output([
        'openssl', 'pkey', '-in', priv_pem, '-pubout', '-outform', 'DER'
    ], stderr=subprocess.DEVNULL)

    sig_entry = lp_bytes(struct.pack('<I', alg_id) + lp_bytes(sig_bytes))
    sigs_seq = lp_bytes(sig_entry)
    signer = lp_bytes(lp_bytes(signed_data) + sigs_seq + lp_bytes(pub_der))
    signers_block = lp_bytes(signer)

    pair_v2 = struct.pack('<I', 0x7109871a) + signers_block
    kv_pair_v2 = struct.pack('<Q', len(pair_v2)) + pair_v2

    # Pad block so total APK Signing Block size is a multiple of 4096 bytes
    base_len = 8 + len(kv_pair_v2) + 8 + 16
    rem = base_len % 4096
    if rem != 0:
        pad_needed = (4096 - rem) % 4096
        if pad_needed < 12:
            pad_needed += 4096
        pad_val = b'\x00' * (pad_needed - 12)
        pair_pad = struct.pack('<I', 0x42726577) + pad_val
        kv_pair_pad = struct.pack('<Q', len(pair_pad)) + pair_pad
        kv_pairs = kv_pair_v2 + kv_pair_pad
    else:
        kv_pairs = kv_pair_v2

    block_size_field = len(kv_pairs) + 8 + 16
    apk_sig_block = (
        struct.pack('<Q', block_size_field) +
        kv_pairs +
        struct.pack('<Q', block_size_field) +
        b'APK Sig Block 42'
    )

    new_cd_offset = cd_offset + len(apk_sig_block)
    struct.pack_into('<I', eocd_section, 16, new_cd_offset)

    with open(out_apk, 'wb') as f:
        f.write(before_cd)
        f.write(apk_sig_block)
        f.write(cd_section)
        f.write(eocd_section)

if __name__ == '__main__':
    build_and_sign_apk(sys.argv[1], sys.argv[2])
