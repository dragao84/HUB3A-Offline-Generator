import sys
from pathlib import Path
from tkinter import Tk, Frame, Label, Entry, Button, StringVar, filedialog, messagebox
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from core.hub3a import Payment, make_png, make_pdf

labels = [('amount','Iznos (EUR)'),('recipient','Primatelj'),('recipient_street','Ulica primatelja'),
('recipient_city','Pošta i mjesto primatelja'),('iban','IBAN primatelja'),('model','Model'),
('reference','Poziv na broj'),('purpose_code','Šifra namjene'),('description','Opis plaćanja'),
('payer','Platitelj (opcionalno)'),('payer_street','Ulica platitelja'),('payer_city','Pošta i mjesto platitelja')]
root=Tk(); root.title('HUB 3A • PDF417 offline generator'); root.geometry('640x640'); root.resizable(True,True)
frame=Frame(root,padx=22,pady=16); frame.pack(fill='both',expand=True)
Label(frame,text='HUB 3A • Offline generator PDF417',font=('Segoe UI',16,'bold')).grid(row=0,column=0,columnspan=2,sticky='w',pady=(0,16))
vars={}
for i,(key,label) in enumerate(labels,1):
    Label(frame,text=label,anchor='w').grid(row=i,column=0,sticky='w',padx=(0,12),pady=5)
    var=StringVar(value='HR99' if key=='model' else '')
    Entry(frame,textvariable=var,width=48).grid(row=i,column=1,sticky='ew',pady=5)
    vars[key]=var
frame.columnconfigure(1,weight=1)
def save():
    try:
        payment=Payment(**{k:v.get().strip() for k,v in vars.items()})
        path=filedialog.asksaveasfilename(defaultextension='.png',filetypes=[('PNG slika','*.png'),('PDF dokument','*.pdf')],initialfile='HUB3A_barkod.png')
        if not path: return
        Path(path).write_bytes(make_pdf(payment) if path.lower().endswith('.pdf') else make_png(payment))
        messagebox.showinfo('Spremljeno','Barkod je spremljen. U Wordu odaberite Umetni → Slike. Prije slanja provjerite barkod u mobilnom bankarstvu.')
    except Exception as ex: messagebox.showerror('Provjera podataka',str(ex))
Button(frame,text='SPREMI PNG ILI PDF',command=save,bg='#1455ca',fg='white',padx=20,pady=10).grid(row=len(labels)+1,column=0,columnspan=2,pady=20)
Label(frame,text='Podaci ostaju na uređaju. Aplikacija ne koristi internetske servise.',fg='#555').grid(row=len(labels)+2,column=0,columnspan=2)
root.mainloop()
