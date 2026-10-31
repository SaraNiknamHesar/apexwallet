"""Seed a few loan samples into the running ApexWallet demo (local profile, in-memory)."""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:37917"
PASSWORDS = {
    "alice": "alice-pass-1",
    "bob": "bob-pass-1111",
    "sam": "sam-supervisor-1",
    "tim": "tim-teller-1",
    "carol": "carol-pass-1",
    "dave": "dave-pass-1",
}


def call(method, path, token=None, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, raw


def login(user):
    status, body = call("POST", "/api/v1/auth/login", body={"username": user, "password": PASSWORDS[user]})
    if status != 200:
        raise SystemExit(f"login {user} failed: {status} {body}")
    return body["accessToken"]


def register_and_open(user):
    status, body = call("POST", "/api/v1/auth/register", body={"username": user, "password": PASSWORDS[user]})
    if status not in (200, 201, 204):
        print(f"  register {user}: {status} {body}")
    tim = login("tim")
    status, body = call("POST", "/api/v1/accounts", tim,
                        {"ownerRef": user, "currency": "EUR", "type": "CUSTOMER_LIABILITY"})
    print(f"  account for {user}: {status}")
    return body


def show(label, status, body):
    if isinstance(body, dict):
        short = {k: body.get(k) for k in ("status", "principalMinor", "outstandingMinor", "httpStatus") if k in body}
        print(f"{label}: HTTP {status} {short}")
    else:
        print(f"{label}: HTTP {status} {body}")
    return body


sam = login("sam")
print("== 1. alice asks for 100.00 (under the 500.00 instant limit) ==")
alice = login("alice")
st, loan = call("POST", "/api/v1/loans", alice, {"amountMinor": 10000})
show("alice 100.00", st, loan)

print("== 2. bob asks for 900.00 (over the limit -> waits for sam) ==")
bob = login("bob")
st, pending = call("POST", "/api/v1/loans", bob, {"amountMinor": 90000})
show("bob 900.00", st, pending)

print("== 3. carol: 900.00 asked, then approved by sam ==")
register_and_open("carol")
carol = login("carol")
st, c_loan = call("POST", "/api/v1/loans", carol, {"amountMinor": 90000})
show("carol 900.00 ask", st, c_loan)
st, c_loan = call("POST", f"/api/v1/loans/{c_loan['id']}/approve", sam)
show("carol 900.00 approve", st, c_loan)

print("== 4. dave: 50.00 instant, then repaid in full ==")
register_and_open("dave")
dave = login("dave")
st, d_loan = call("POST", "/api/v1/loans", dave, {"amountMinor": 5000})
show("dave 50.00 ask", st, d_loan)
st, d_loan = call("POST", f"/api/v1/loans/{d_loan['id']}/repay", dave, {"amountMinor": 5000})
show("dave 50.00 repay", st, d_loan)

print("== 5. carol repays 200.00 of her 900.00 (partial) ==")
st, c_loan = call("POST", f"/api/v1/loans/{c_loan['id']}/repay", carol, {"amountMinor": 20000})
show("carol repay 200.00", st, c_loan)

print("\n== final loan book as sam ==")
st, rows = call("GET", "/api/v1/loans", sam)
for r in rows:
    print(f"  {r['borrowerUsername']:6} asked {r['principalMinor']/100:8.2f}  open "
          f"{r['outstandingMinor']/100:8.2f}  {r['status']}")

print("\n== balances ==")
for user in ("alice", "bob", "carol", "dave"):
    tok = login(user)
    st, acc = call("GET", "/api/v1/accounts", tok)
    print(f"  {user:6} {acc[0]['balanceMinor']/100:9.2f} EUR")
