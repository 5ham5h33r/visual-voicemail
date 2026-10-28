"""Load local signing references without putting passwords in command arguments."""
import json
from pathlib import Path


def signing_args(config_path, root):
    root = Path(root)
    config = json.loads(Path(config_path).read_text(encoding='utf-8'))
    signers = config.get('signers', [])
    if not signers:
        raise ValueError('Signing configuration needs at least one signer.')
    if signers[-1].get('development_key'):
        raise ValueError('The current release signer must be a dedicated release key.')
    args = []
    for index, signer in enumerate(signers):
        keystore = (root / signer['keystore']).resolve()
        if not keystore.is_file() or not signer.get('alias'):
            raise ValueError('Signing keystore or alias is missing.')
        if index:
            args.append('--next-signer')
        args += ['--ks', str(keystore), '--ks-key-alias', signer['alias']]
        if signer.get('password_file'):
            password = (root / signer['password_file']).resolve()
            if not password.is_file():
                raise ValueError('Signing password file is missing.')
            args += ['--ks-pass', 'file:' + str(password)]
        elif signer.get('development_key') is True:
            args += ['--ks-pass', 'pass:android']
        else:
            raise ValueError('Release signing requires a password file.')
    if config.get('lineage'):
        lineage = (root / config['lineage']).resolve()
        if not lineage.is_file():
            raise ValueError('Signing lineage is missing.')
        args += ['--lineage', str(lineage), '--rotation-min-sdk-version', '33']
    elif len(signers) > 1:
        raise ValueError('Multiple signing references require a rotation lineage.')
    return args
