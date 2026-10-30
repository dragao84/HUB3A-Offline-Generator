
import json
import os
import sys
from pathlib import Path
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from core.hub3a import Payment, make_png, make_pdf

BG = '#F4F7FB'
WHITE = '#FFFFFF'
NAVY = '#15243C'
MUTED = '#62718A'
BLUE = '#215BE3'
BORDER = '#DDE5F0'

FIELDS = [
    ('amount', 'Iznos (EUR)', '0,00'),
    ('recipient', 'Primatelj', 'Naziv primatelja'),
    ('recipient_street', 'Ulica primatelja', 'Ulica i broj'),
    ('recipient_city', 'Pošta i mjesto primatelja', 'Poštanski broj i mjesto'),
    ('iban', 'IBAN primatelja', 'HR...'),
    ('model', 'Model', 'HR99'),
    ('reference', 'Poziv na broj', 'Poziv na broj primatelja'),
    ('purpose_code', 'Šifra namjene', 'Opcionalno'),
    ('description', 'Opis plaćanja', 'Npr. Plaćanje prema ponudi'),
    ('payer', 'Platitelj (opcionalno)', 'Ime ili naziv platitelja'),
    ('payer_street', 'Ulica platitelja', 'Ulica i broj'),
    ('payer_city', 'Pošta i mjesto platitelja', 'Poštanski broj i mjesto'),
]

root = tk.Tk()
root.title('HUB-3A Studio · Offline PDF417')
root.geometry('850x780')
root.minsize(680, 600)
root.configure(bg=BG)

style = ttk.Style(root)
style.theme_use('clam')
style.configure(
    'TEntry', padding=9, relief='flat',
    fieldbackground=WHITE, foreground=NAVY,
    borderwidth=1
)
style.configure('TScrollbar', background=BORDER, troughcolor=BG)

header = tk.Frame(root, bg=NAVY, padx=27, pady=21)
header.pack(fill='x')

tk.Label(
    header, text='HUB-3A  /  PDF417',
    bg=NAVY, fg=WHITE,
    font=('Segoe UI', 21, 'bold')
).pack(anchor='w')

tk.Label(
    header,
    text='Lokalni generator barkoda za račune i ponude  •  Bez interneta',
    bg=NAVY, fg='#B8C9E7',
    font=('Segoe UI', 10)
).pack(anchor='w', pady=(4, 0))

area = tk.Frame(root, bg=BG)
area.pack(fill='both', expand=True)

canvas = tk.Canvas(area, bg=BG, highlightthickness=0)
bar = ttk.Scrollbar(area, orient='vertical', command=canvas.yview)
canvas.configure(yscrollcommand=bar.set)
bar.pack(side='right', fill='y')
canvas.pack(side='left', fill='both', expand=True)

content = tk.Frame(canvas, bg=BG, padx=26, pady=21)
window = canvas.create_window((0, 0), window=content, anchor='nw')

content.bind(
    '<Configure>',
    lambda e: canvas.configure(scrollregion=canvas.bbox('all'))
)
canvas.bind(
    '<Configure>',
    lambda e: canvas.itemconfigure(window, width=e.width)
)
canvas.bind_all(
    '<MouseWheel>',
    lambda e: canvas.yview_scroll(-int(e.delta / 120), 'units')
)

vars = {}

def card(title, subtitle):
    outer = tk.Frame(
        content, bg=WHITE, padx=22, pady=17,
        highlightbackground=BORDER, highlightthickness=1
    )
    outer.pack(fill='x', pady=(0, 15))

    tk.Label(
        outer, text=title, fg=NAVY, bg=WHITE,
        font=('Segoe UI', 13, 'bold')
    ).pack(anchor='w')

    tk.Label(
        outer, text=subtitle, fg=MUTED, bg=WHITE,
        font=('Segoe UI', 9)
    ).pack(anchor='w', pady=(3, 12))
    return outer

def add_field(parent, key, label, hint):
    row = tk.Frame(parent, bg=WHITE)
    row.pack(fill='x', pady=(0, 10))

    tk.Label(
        row, text=label, bg=WHITE, fg=NAVY,
        font=('Segoe UI', 10, 'bold')
    ).pack(anchor='w', pady=(0, 5))

    var = tk.StringVar(value='HR99' if key == 'model' else '')
    entry = ttk.Entry(row, textvariable=var, font=('Segoe UI', 11))
    entry.pack(fill='x')
    vars[key] = var

primary = card(
    'Podaci za plaćanje',
    'Unesi podatke koji će biti zapisani u barkod.'
)
for key, label, hint in FIELDS[:9]:
    add_field(primary, key, label, hint)

advanced = tk.Frame(content, bg=BG)
advanced_visible = tk.BooleanVar(value=False)

def toggle_advanced():
    if advanced_visible.get():
        advanced.pack_forget()
        advanced_visible.set(False)
        advanced_btn.configure(text='＋  Podaci platitelja (opcionalno)')
    else:
        advanced.pack(fill='x', before=actions)
        advanced_visible.set(True)
        advanced_btn.configure(text='－  Sakrij podatke platitelja')

advanced_btn = tk.Button(
    content,
    text='＋  Podaci platitelja (opcionalno)',
    command=toggle_advanced,
    bg=BG, fg=BLUE, relief='flat',
    font=('Segoe UI', 10, 'bold'),
    cursor='hand2'
)
advanced_btn.pack(anchor='w', pady=(0, 13))

adv_card = tk.Frame(
    advanced, bg=WHITE, padx=22, pady=18,
    highlightbackground=BORDER, highlightthickness=1
)
adv_card.pack(fill='x', pady=(0, 15))

for key, label, hint in FIELDS[9:]:
    add_field(adv_card, key, label, hint)

actions = card(
    'Izvoz i predložak',
    'PNG za Word ili PDF za ispis. Podatke možeš zapamtiti na uređaju.'
)
status = tk.StringVar(value='Spremno za generiranje • HUB-3A EUR')

def settings_path():
    home = (
        Path(os.environ.get('APPDATA') or Path.home())
        if os.name == 'nt'
        else Path.home() / '.config'
    )
    directory = home / 'HUB3A_Generator'
    directory.mkdir(parents=True, exist_ok=True)
    return directory / 'saved_fields.json'

def remember():
    try:
        path = settings_path()
        temp = path.with_suffix('.tmp')
        temp.write_text(
            json.dumps(
                {k: v.get().strip() for k, v in vars.items()},
                ensure_ascii=False, indent=2
            ),
            encoding='utf-8'
        )
        temp.replace(path)
        status.set('Predložak je lokalno spremljen.')
        messagebox.showinfo(
            'Predložak spremljen',
            'Podaci su zapamćeni na ovom računalu.'
        )
    except Exception as ex:
        messagebox.showerror('Spremanje', str(ex))

def restore():
    try:
        path = settings_path()
        if path.exists():
            saved = json.loads(path.read_text(encoding='utf-8'))
            for key, var in vars.items():
                if isinstance(saved.get(key), str):
                    var.set(saved[key])
    except Exception as ex:
        messagebox.showwarning('Učitavanje predloška', str(ex))

def save():
    try:
        payment = Payment(
            **{key: v.get().strip() for key, v in vars.items()}
        )
        path = filedialog.asksaveasfilename(
            defaultextension='.png',
            filetypes=[
                ('PNG slika', '*.png'),
                ('PDF dokument', '*.pdf')
            ],
            initialfile='HUB3A_barkod.png'
        )
        if not path:
            return

        data = (
            make_pdf(payment)
            if path.lower().endswith('.pdf')
            else make_png(payment)
        )
        Path(path).write_bytes(data)
        status.set('Barkod je uspješno spremljen.')
        messagebox.showinfo(
            'Barkod spremljen',
            'Datoteka je spremljena. Prije uplate provjerite očitane podatke u banci.'
        )
    except Exception as ex:
        status.set('Provjerite unesene podatke ili generiranje barkoda.')
        messagebox.showerror('Provjera podataka', str(ex))

btnrow = tk.Frame(actions, bg=WHITE)
btnrow.pack(fill='x')

tk.Button(
    btnrow,
    text='SPREMI PNG ILI PDF',
    command=save,
    bg=BLUE, fg=WHITE,
    activebackground='#1747B6',
    activeforeground=WHITE,
    relief='flat',
    font=('Segoe UI', 11, 'bold'),
    padx=18, pady=12,
    cursor='hand2'
).pack(side='left', padx=(0, 10))

tk.Button(
    btnrow,
    text='ZAPAMTI PODATKE',
    command=remember,
    bg='#EAF0FF', fg=BLUE,
    relief='flat',
    font=('Segoe UI', 10, 'bold'),
    padx=16, pady=13,
    cursor='hand2'
).pack(side='left')

tk.Label(
    actions,
    textvariable=status,
    bg=WHITE, fg=MUTED,
    font=('Segoe UI', 9)
).pack(anchor='w', pady=(12, 0))

tk.Label(
    content,
    text='OFFLINE  •  Podaci se obrađuju na uređaju  •  Prije plaćanja provjerite sve stavke.',
    bg=BG, fg=MUTED,
    font=('Segoe UI', 9)
).pack(anchor='w', pady=(0, 15))

restore()
root.mainloop()
