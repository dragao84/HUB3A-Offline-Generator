
import pytest
from core.hub3a import Payment, payload, valid_hr_iban


def test_valid_iban():
    assert valid_hr_iban("HR1210010051863000160")


def test_invalid_iban():
    assert not valid_hr_iban("HR1210010051863000161")


def test_valid_payload():
    p = Payment(
        amount="12.50",
        recipient="TEST PRIMATELJ",
        iban="HR1210010051863000160",
        model="HR99",
        description="TEST PLACANJE"
    )
    data = payload(p)
    assert data.startswith(b"HRVHUB30\nEUR\n")
    assert b"000000000001250" in data


def test_invalid_amount():
    p = Payment(
        amount="-5",
        recipient="TEST",
        iban="HR1210010051863000160",
        description="TEST"
    )
    with pytest.raises(ValueError):
        payload(p)
