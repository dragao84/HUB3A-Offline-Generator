
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
style.configure('TEntry', padding=9, relief='flat',
                fieldbackground=WHITE, foreground=NAVY, borderwidth=1)
style.configure('TScrollbar', background=BORDER, troughcolor=BG)

header = tk.Frame(root, bg=NAVY, padx=27, pady=21)
header.pack(fill='x')
tk.Label(header, text='HUB-3A  /  PDF417', bg=NAVY, fg=WHITE,
         font=('Segoe UI', 21, 'bold')).pack(anchor='w')
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

CONTACT_FIELDS = {
    'payers': ('payer', 'payer_street', 'payer_city'),
    'recipients': (
        'recipient', 'recipient_street', 'recipient_city',
        'iban', 'model', 'reference'
    ),
    'accounts': (
        'label', 'recipient', 'recipient_street', 'recipient_city',
        'iban', 'model', 'reference'
    ),
    'templates': (
        'label', 'recipient', 'recipient_street', 'recipient_city',
        'iban', 'model', 'reference', 'purpose_code', 'description'
    )
}

contacts = {
    'version': 1,
    'payers': [],
    'recipients': [],
    'accounts': [],
    'templates': []
}
contact_selectors = {}

def settings_path():
    home = (
        Path(os.environ.get('APPDATA') or Path.home())
        if os.name == 'nt'
        else Path.home() / '.config'
    )
    directory = home / 'HUB3A_Generator'
    directory.mkdir(parents=True, exist_ok=True)
    return directory / 'saved_fields.json'

def contacts_path():
    return settings_path().with_name('contacts.json')

def check_contacts(data):
    if not isinstance(data, dict) or data.get('version') != 1:
        raise ValueError('Nepodržan format adresara.')

    checked = {'version': 1}
    for section, keys in CONTACT_FIELDS.items():
        records = data.get(section, [])
        if not isinstance(records, list) or len(records) > 1000:
            raise ValueError('Neispravan popis zapisa u adresaru.')

        out = []
        for record in records:
            if not isinstance(record, dict):
                raise ValueError('Neispravan zapis adresara.')

            entry = {k: record.get(k, '') for k in keys}
            if not all(
                isinstance(v, str) and len(v) <= 300
                for v in entry.values()
            ):
                raise ValueError('Neispravna vrijednost u adresaru.')

            if not entry[keys[0]].strip():
                raise ValueError('Zapis bez naziva nije dopušten.')
            out.append(entry)

        checked[section] = out
    return checked

def write_contacts():
    path = contacts_path()
    tmp = path.with_suffix('.tmp')
    tmp.write_text(
        json.dumps(contacts, ensure_ascii=False, indent=2),
        encoding='utf-8'
    )
    tmp.replace(path)

def refresh_contacts():
    for section, combo in contact_selectors.items():
        names = [
            entry[CONTACT_FIELDS[section][0]]
            for entry in contacts[section]
        ]
        combo['values'] = ['— Odaberi —'] + [
            f'{i+1}. {name}' for i, name in enumerate(names)
        ]
        combo.current(0)

def selected_contact(section):
    index = contact_selectors[section].current() - 1
    return index if 0 <= index < len(contacts[section]) else -1

def apply_contact(section):
    idx = selected_contact(section)
    if idx < 0:
        return
    for key, value in contacts[section][idx].items():
        if key in vars:
            vars[key].set(value)
    if section in ('accounts', 'templates'):
        status.set('Odabran zapis: provjerite IBAN prije izvoza.')

def store_contact(section):
    keys = CONTACT_FIELDS[section]
    entry = {
        key: vars[key].get().strip()
        for key in keys if key != 'label'
    }

    if 'label' in keys:
        from tkinter import simpledialog
        old = (
            contacts[section][selected_contact(section)]['label']
            if selected_contact(section) >= 0 else ''
        )
        label = simpledialog.askstring(
            'Naziv zapisa', 'Naziv računa / predloška:',
            initialvalue=old, parent=root
        )
        if label is None:
            return
        entry['label'] = label.strip()

    if not entry[keys[0]]:
        messagebox.showwarning('Adresar', 'Prvo unesite naziv.')
        return

    idx = selected_contact(section)
    if section == 'accounts' and not entry.get('iban'):
        messagebox.showwarning('Račun', 'IBAN ne smije biti prazan.')
        return

    if idx >= 0:
        contacts[section][idx] = entry
    else:
        contacts[section].append(entry)

    write_contacts()
    refresh_contacts()
    status.set('Adresar je lokalno spremljen.')

def remove_contact(section):
    idx = selected_contact(section)
    if idx < 0:
        return
    if messagebox.askyesno('Brisanje', 'Obrisati odabrani zapis?'):
        del contacts[section][idx]
        write_contacts()
        refresh_contacts()

def export_contacts():
    path = filedialog.asksaveasfilename(
        defaultextension='.json',
        initialfile='HUB3A_adresar.json',
        filetypes=[('JSON', '*.json')]
    )
    if path:
        Path(path).write_text(
            json.dumps(contacts, ensure_ascii=False, indent=2),
            encoding='utf-8'
        )
        messagebox.showinfo('Izvoz', 'Adresar je izvezen.')

def import_contacts():
    path = filedialog.askopenfilename(
        filetypes=[('JSON', '*.json')]
    )
    if not path:
        return

    try:
        data = check_contacts(
            json.loads(Path(path).read_text(encoding='utf-8'))
        )
        if not messagebox.askyesno(
            'Uvoz adresara',
            'Zamijeniti SVE postojeće adresare, račune i predloške uvezenima?'
        ):
            return

        contacts.update(data)
        write_contacts()
        refresh_contacts()
    except Exception as exc:
        messagebox.showerror('Uvoz', str(exc))

book = card(
    'Adresari i računi',
    'Odabirom računa popunjava se primatelj i IBAN. Iznos ostaje nepromijenjen.'
)

for section, title in [
    ('recipients', 'Primatelji'),
    ('payers', 'Platitelji'),
    ('accounts', 'Bankovni računi (više IBAN-a po primatelju)'),
    ('templates', 'Predlošci plaćanja')
]:
    tk.Label(
        book, text=title, bg=WHITE, fg=NAVY,
        font=('Segoe UI', 10, 'bold')
    ).pack(anchor='w')

    combo = ttk.Combobox(
        book, state='readonly', font=('Segoe UI', 10)
    )
    combo.pack(fill='x', pady=(4, 5))
    contact_selectors[section] = combo
    combo.bind(
        '<<ComboboxSelected>>',
        lambda event, kind=section: apply_contact(kind)
    )

    controls = tk.Frame(book, bg=WHITE)
    controls.pack(anchor='w', pady=(0, 12))

    for label, function in [
        ('Dodaj / Ažuriraj', store_contact),
        ('Obriši', remove_contact)
    ]:
        tk.Button(
            controls, text=label,
            command=lambda kind=section, action=function: action(kind),
            bg='#EAF0FF', fg=BLUE, relief='flat',
            padx=12, pady=6
        ).pack(side='left', padx=(0, 8))

transfer = tk.Frame(book, bg=WHITE)
transfer.pack(anchor='w')
tk.Button(
    transfer, text='Izvezi JSON', command=export_contacts,
    bg='#EAF0FF', fg=BLUE, relief='flat'
).pack(side='left', padx=(0, 9))
tk.Button(
    transfer, text='Uvezi JSON', command=import_contacts,
    bg='#EAF0FF', fg=BLUE, relief='flat'
).pack(side='left')

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
        advanced_btn.configure(
            text='＋  Podaci platitelja (opcionalno)'
        )
    else:
        advanced.pack(fill='x', before=actions)
        advanced_visible.set(True)
        advanced_btn.configure(
            text='－  Sakrij podatke platitelja'
        )

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
status = tk.StringVar(
    value='Spremno za generiranje • HUB-3A EUR'
)

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
            saved = json.loads(
                path.read_text(encoding='utf-8')
            )
            for key, var in vars.items():
                if isinstance(saved.get(key), str):
                    var.set(saved[key])
    except Exception as ex:
        messagebox.showwarning(
            'Učitavanje predloška', str(ex)
        )

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
        status.set(
            'Provjerite unesene podatke ili generiranje barkoda.'
        )
        messagebox.showerror(
            'Provjera podataka', str(ex)
        )

btnrow = tk.Frame(actions, bg=WHITE)
btnrow.pack(fill='x')
tk.Button(
    btnrow, text='SPREMI PNG ILI PDF',
    command=save, bg=BLUE, fg=WHITE,
    activebackground='#1747B6', activeforeground=WHITE,
    relief='flat', font=('Segoe UI', 11, 'bold'),
    padx=18, pady=12, cursor='hand2'
).pack(side='left', padx=(0, 10))

tk.Button(
    btnrow, text='ZAPAMTI PODATKE',
    command=remember, bg='#EAF0FF', fg=BLUE,
    relief='flat', font=('Segoe UI', 10, 'bold'),
    padx=16, pady=13, cursor='hand2'
).pack(side='left')

tk.Label(
    actions, textvariable=status, bg=WHITE, fg=MUTED,
    font=('Segoe UI', 9)
).pack(anchor='w', pady=(12, 0))

tk.Label(
    content,
    text='OFFLINE  •  Podaci se obrađuju na uređaju  •  Prije plaćanja provjerite sve stavke.',
    bg=BG, fg=MUTED,
    font=('Segoe UI', 9)
).pack(anchor='w', pady=(0, 15))

restore()
try:
    path = contacts_path()
    if path.exists():
        contacts.update(
            check_contacts(
                json.loads(
                    path.read_text(encoding='utf-8')
                )
            )
        )
except Exception as exc:
    messagebox.showwarning(
        'Adresar', 'Adresar nije učitan: ' + str(exc)
    )

refresh_contacts()
root.mainloop()
