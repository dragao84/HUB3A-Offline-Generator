from core.hub3a import Payment,make_png,make_pdf
from pathlib import Path
p=Payment(amount="10,00",recipient="PRIMJER PRIMATELJA",recipient_street="ULICA 1",recipient_city="21000 SPLIT",iban="HR1210010051863000160",model="HR99",description="PRIMJER - NE PLACATI")
Path("DEMO_BARKOD.png").write_bytes(make_png(p))
Path("DEMO_BARKOD.pdf").write_bytes(make_pdf(p))
print("Spremljeni DEMO_BARKOD.png i DEMO_BARKOD.pdf")
