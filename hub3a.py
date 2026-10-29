"""HUB3A EUR v6 payload; no network activity."""
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
import re
import io

LIMITS = (8,3,15,30,27,27,25,25,27,21,4,22,4,35)
ALLOWED = re.compile(r"^[0-9A-Za-zČĆĐŠŽčćđšž ,.:+?'()/\-]*$")

@dataclass
class Payment:
    amount: str = ""
    payer: str = ""
    payer_street: str = ""
    payer_city: str = ""
    recipient: str = ""
    recipient_street: str = ""
    recipient_city: str = ""
    iban: str = ""
    model: str = "HR99"
    reference: str = ""
    purpose_code: str = ""
    description: str = ""


def valid_hr_iban(s):
    if not re.fullmatch(r'HR\d{19}', s): return False
    rearranged = s[4:] + '1727' + s[2:4]
    rem = 0
    for char in rearranged: rem = (rem * 10 + int(char)) % 97
    return rem == 1


def payload(p: Payment):
    try:
        v = Decimal(p.amount.strip().replace(' ', '').replace(',', '.'))
    except (InvalidOperation, ValueError):
        raise ValueError('Iznos nije ispravan.')
    if not v.is_finite() or v <= 0 or v.as_tuple().exponent < -2:
        raise ValueError('Iznos mora biti veći od nule i imati najviše dvije decimale.')
    cents = int((v * 100).to_integral_value(rounding=ROUND_HALF_UP))
    if cents >= 10**15: raise ValueError('Iznos je prevelik.')
    iban = re.sub(r'\s+', '', p.iban).upper()
    if not valid_hr_iban(iban): raise ValueError('IBAN nije valjan (HR, 21 znak, MOD 97).')
    model = p.model.strip().upper()
    if not re.fullmatch(r'HR\d{2}', model): raise ValueError('Model mora biti u obliku HR00, HR01, HR99...')
    vals = ['HRVHUB30', 'EUR', f'{cents:015d}', p.payer, p.payer_street,
            p.payer_city, p.recipient, p.recipient_street, p.recipient_city,
            iban, model, p.reference, p.purpose_code, p.description]
    for i, (value, limit) in enumerate(zip(vals, LIMITS), 1):
        if '\n' in value or '\r' in value: raise ValueError(f'Polje {i} sadrži novi red.')
        if len(value) > limit: raise ValueError(f'Polje {i} prelazi {limit} znakova.')
        if not ALLOWED.fullmatch(value): raise ValueError(f'Polje {i} sadrži nedopuštene znakove.')
    if not p.recipient.strip() or not p.description.strip():
        raise ValueError('Unesite primatelja i opis plaćanja.')
    return ('\n'.join(vals) + '\n').encode('utf-8')


def _ps_for_payment(p):
    from pathlib import Path
    """Create PostScript using bundled Barcode Writer in Pure PostScript (MIT)."""
    data = payload(p)
    # PostScript hex string retains UTF-8 byte values exactly.
    prefix = (Path(__file__).resolve().parent / 'bwipp.ps').read_bytes()
    commands = ("\n%!PS-Adobe-3.0\n"
                "<< /PageSize [360 170] >> setpagedevice\n"
                "20 30 translate 0 0 moveto\n"
                f"<{data.hex()}> (columns=9 eclevel=4 rowmult=3 encoding=byte) "
                "/pdf417 /uk.co.terryburton.bwipp findresource exec\nshowpage\n")
    return prefix + commands.encode('ascii')


def _gs(args, ps):
    import subprocess, shutil, os, sys
    base = Path(getattr(sys, '_MEIPASS', Path(__file__).resolve().parents[1]))
    bundled = base / 'gs' / 'bin' / 'gswin64c.exe'
    exe = str(bundled) if bundled.exists() else (shutil.which('gswin64c') or shutil.which('gswin32c') or shutil.which('gs'))
    if exe is None:
        raise RuntimeError('Nije pronađen Ghostscript. Instalirajte Ghostscript i dodajte ga u PATH.')
    env = os.environ.copy()
    if bundled.exists():
        gsroot = base / 'gs'
        env['GS_LIB'] = os.pathsep.join(str(gsroot / x) for x in ('Resource/Init','Resource/Font','lib'))
    result = subprocess.run([exe,'-q','-dSAFER','-dBATCH','-dNOPAUSE',*args,'-'],
                            input=ps,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30,env=env)
    if result.returncode:
        raise RuntimeError('Generiranje barkoda nije uspjelo: '+result.stderr.decode('utf-8','replace')[:500])
    return result.stdout


def make_png(p, scale=3):
    import tempfile
    from pathlib import Path
    # Ghostscript writes image to a named file; no network, no external service.
    with tempfile.TemporaryDirectory() as td:
        out=Path(td)/'barcode.png'
        _gs(['-sDEVICE=pngmono','-r300',f'-sOutputFile={out}'],_ps_for_payment(p))
        data=out.read_bytes()
        if not data.startswith(b'\x89PNG\r\n\x1a\n'):
            raise RuntimeError('Izvoz nije proizveo PNG datoteku.')
        return data


def make_pdf(p):
    data=_gs(['-sDEVICE=pdfwrite','-sOutputFile=-'],_ps_for_payment(p))
    if not data.startswith(b'%PDF-'):
        raise RuntimeError('Izvoz nije proizveo PDF datoteku.')
    return data
