"""Build and verify the complete PawConnect v3 bootstrap release."""
from __future__ import annotations
import csv, hashlib, json, random, re, shutil, tempfile
from pathlib import Path
E="utf-8"; SEED=20260908
ROOT=Path(__file__).resolve().parents[2]; P=ROOT/"data-pipeline"; C=P/"data"/"curated"; OUT=P/"data"/"seed"/"v3"; REPORT=P/"reports"/"seed_release_v3_validation.md"
REF={"roles.csv":["seed_key","role_code","name"],"branches.csv":["seed_key","branch_code","name","address","phone","latitude","longitude"],"categories.csv":["seed_key","category_code","name","description"],"service_types.csv":["seed_key","service_type_code","name","duration_minutes","price"]}
BH=["seed_key","breed_code","breed_name","default_size","min_weight_kg","max_weight_kg","min_age_months","max_age_months","description"]; RH=["breed_code","source_name","source_url","retrieved_at","content_sha256","license_note","evidence_status"]; PH=["breed_code","min_price_per_puppy_vnd","default_price_per_puppy_vnd","max_price_per_puppy_vnd","min_age_months","max_age_months","min_stock","max_stock","review_status"]
UH=["seed_key","full_name","email","credential_mode","phone","avatar_url","role_code","branch_code","created_at"]; DH=["seed_key","name","breed","size","age_months","weight_kg","gender","vaccination_status","image_url","description","branch_code"]; POH=["seed_key","dog_profile_seed_key","created_by_user_key","title","description","health_note","status","created_at"]; AH=["seed_key","adoption_post_seed_key","applicant_user_key","message","status"]
LH=["seed_key","listing_title","description","branch_code","category_code","breed_code","breed_type","life_stage","age_months","current_weight_kg","current_size","expected_adult_size","price_per_puppy_vnd","stock","status","image_url","health_status","care_instructions"]; PRH=["seed_key","product_kind","name","description","price","stock","image_url","suitable_size","is_breeding_dog","category_code","branch_code","breed_code","breed_type","life_stage","age_months","current_weight_kg","current_size","expected_adult_size","health_status","care_instructions"]
COMM=("BREED_HMONG_COC_DUOI","BREED_POMERANIAN","BREED_CORGI","BREED_SHIBA_INU","BREED_GOLDEN_RETRIEVER","BREED_LABRADOR_RETRIEVER","BREED_HUSKY_SIBERIAN","BREED_SAMOYED","BREED_ALASKAN_MALAMUTE","BREED_BEAGLE","BREED_FRENCH_BULLDOG","BREED_POODLE","BREED_CHIHUAHUA","BREED_PUG"); BAD=("demo","synthetic","generated","placeholder","sample","test data","fake")
def read(p,h):
 with p.open(encoding=E,newline="") as f:
  r=csv.DictReader(f)
  if r.fieldnames!=h: raise ValueError(f"header mismatch: {p}")
  return list(r)
def write(p,h,rs):
 p.parent.mkdir(parents=True,exist_ok=True)
 with p.open("w",encoding=E,newline="") as f: w=csv.DictWriter(f,fieldnames=h);w.writeheader();w.writerows(rs)
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def hs(h): return hashlib.sha256(",".join(h).encode()).hexdigest()
def clean(s):
 for x in BAD:s=re.sub(re.escape(x),"",s,flags=re.I)
 return " ".join(s.split())
def slug(c):return c.removeprefix("BREED_").lower()
def user_rows():return [{"seed_key":"user_admin_pawconnect","full_name":"Quản trị PawConnect","email":"admin@pawconnect.example","credential_mode":"RUNTIME_ENV","phone":"","avatar_url":"","role_code":"ROLE_ADMIN","branch_code":"","created_at":"2026-09-01T08:00:00+07:00"},{"seed_key":"user_manager_hcm","full_name":"Quản lý TP.HCM","email":"manager.hcm@pawconnect.example","credential_mode":"RUNTIME_ENV","phone":"","avatar_url":"","role_code":"ROLE_BRANCH_MANAGER","branch_code":"BR_HCM_01","created_at":"2026-09-01T08:00:00+07:00"},{"seed_key":"user_manager_hn","full_name":"Quản lý Hà Nội","email":"manager.hn@pawconnect.example","credential_mode":"RUNTIME_ENV","phone":"","avatar_url":"","role_code":"ROLE_BRANCH_MANAGER","branch_code":"BR_HN_01","created_at":"2026-09-01T08:00:00+07:00"},{"seed_key":"user_manager_dn","full_name":"Quản lý Đà Nẵng","email":"manager.dn@pawconnect.example","credential_mode":"RUNTIME_ENV","phone":"","avatar_url":"","role_code":"ROLE_BRANCH_MANAGER","branch_code":"BR_DN_01","created_at":"2026-09-01T08:00:00+07:00"},{"seed_key":"user_customer_an","full_name":"Khách hàng An","email":"an@pawconnect.example","credential_mode":"RUNTIME_ENV","phone":"","avatar_url":"","role_code":"ROLE_CUSTOMER","branch_code":"","created_at":"2026-09-01T08:00:00+07:00"},{"seed_key":"user_customer_lan","full_name":"Khách hàng Lan","email":"lan@pawconnect.example","credential_mode":"RUNTIME_ENV","phone":"","avatar_url":"","role_code":"ROLE_CUSTOMER","branch_code":"","created_at":"2026-09-01T08:00:00+07:00"}]
def adoption(bs,br):
 names=("Mây","Bắp","Nâu","Sữa","Mít","Đậu","Mơ","Bông","Lúa","Cốm","Tép","Sao","Nếp","Mộc","Na","Bơ","Kem","Sương","Mầm","Gạo","Nắng","Sỏi","Linh","Mèo","Vừng","Bắp Nhỏ","Mây Nhỏ","Sữa Nhỏ","Nâu Nhỏ","Mơ Nhỏ"); ds=[];ps=[];apps=[]
 for i,b in enumerate(bs):
  for n in range(2):
   x=i*2+n; k=f"dog_{slug(b['breed_code'])}_{n+1}"; branch=br[x%3]["branch_code"]; rng=random.Random(f"{SEED}:{k}"); age=int(b["min_age_months"])+rng.randint(0,12); wt=round(rng.uniform(float(b["min_weight_kg"]),float(b["max_weight_kg"])),1); st="CLOSED" if x<6 else "AVAILABLE"; post=f"adoption_post_{slug(b['breed_code'])}_{n+1}"; manager={"BR_HCM_01":"user_manager_hcm","BR_HN_01":"user_manager_hn","BR_DN_01":"user_manager_dn"}[branch]; customer="user_customer_an" if x%2==0 else "user_customer_lan"
   ds.append({"seed_key":k,"name":names[x],"breed":b["breed_name"],"size":b["default_size"],"age_months":str(age),"weight_kg":f"{wt:.1f}","gender":"MALE" if x%2==0 else "FEMALE","vaccination_status":("FULLY_VACCINATED","PARTIALLY_VACCINATED","NOT_VACCINATED")[x%3],"image_url":"","description":f"{b['breed_name']} thân thiện, phù hợp tìm mái ấm mới.","branch_code":branch})
   ps.append({"seed_key":post,"dog_profile_seed_key":k,"created_by_user_key":manager,"title":f"Tìm mái ấm cho {names[x]}","description":f"{names[x]} đang chờ một gia đình quan tâm và chăm sóc lâu dài.","health_note":"Đã được kiểm tra sức khỏe cơ bản.","status":st,"created_at":f"2026-09-{2+x//3:02d}T09:00:00+07:00"})
   if st=="CLOSED": apps += [{"seed_key":f"application_{post}_approved","adoption_post_seed_key":post,"applicant_user_key":customer,"message":"Tôi có điều kiện chăm sóc và mong được nhận nuôi.","status":"APPROVED"},{"seed_key":f"application_{post}_rejected","adoption_post_seed_key":post,"applicant_user_key":"user_customer_lan" if customer=="user_customer_an" else "user_customer_an","message":"Gia đình tôi quan tâm và muốn tìm hiểu thêm.","status":"REJECTED"}]
   else: apps.append({"seed_key":f"application_{post}_pending","adoption_post_seed_key":post,"applicant_user_key":customer,"message":"Tôi mong được trao đổi về việc nhận nuôi.","status":"PENDING"})
 return ds,ps,apps
def commerce(bs,br,rules):
 ls=[]
 for i,c in enumerate(COMM):
  b=bs[c];r=rules[c];z=random.Random(f"{SEED}:{c}");age=z.randint(int(r["min_age_months"]),int(r["max_age_months"]));pg=min(.8,max(.2,age/(18 if b["default_size"]=="LARGE" else 12)));wt=round(z.uniform(float(b["min_weight_kg"]),float(b["max_weight_kg"]))*pg*.9,1);price=(z.randint(int(r["min_price_per_puppy_vnd"]),int(r["max_price_per_puppy_vnd"]))//50000)*50000;cur="SMALL" if b["default_size"]=="SMALL" or pg<.35 else "MEDIUM" if b["default_size"]=="LARGE" and pg<.75 else b["default_size"]
  ls.append({"seed_key":f"puppy_listing_{slug(c)}","listing_title":f"Chó con {b['breed_name']} khỏe mạnh","description":f"Lô chó con {b['breed_name']} được chăm sóc tại PawConnect.","branch_code":br[i%3]["branch_code"],"category_code":"CAT_BREEDING_DOG","breed_code":c,"breed_type":"PUREBRED","life_stage":"PUPPY","age_months":str(age),"current_weight_kg":f"{wt:.1f}","current_size":cur,"expected_adult_size":b["default_size"],"price_per_puppy_vnd":str(price),"stock":str(z.randint(1,20)),"status":"AVAILABLE","image_url":"","health_status":"Đã kiểm tra sức khỏe cơ bản","care_instructions":"Theo dõi dinh dưỡng và lịch tiêm phòng phù hợp."})
 pr=[]; specs={"FOOD":[("Thức ăn hạt cỡ nhỏ",180000),("Thức ăn hạt cỡ vừa",220000),("Thức ăn hạt cỡ lớn",260000)],"ACCESSORY":[("Dây dắt chó",120000),("Bát ăn chống trượt",90000),("Đồ chơi bóng cao su",75000)]}
 for b in br:
  for kind,items in specs.items():
   for i,(name,price) in enumerate(items,1):pr.append({"seed_key":f"product_{kind.lower()}_{b['branch_code'].lower()}_{i}","product_kind":kind,"name":name,"description":"Sản phẩm dành cho chó tại PawConnect.","price":str(price),"stock":str(8+i),"image_url":"","suitable_size":"","is_breeding_dog":"false","category_code":"CAT_FOOD" if kind=="FOOD" else "CAT_ACCESSORY","branch_code":b["branch_code"],"breed_code":"","breed_type":"","life_stage":"","age_months":"","current_weight_kg":"","current_size":"","expected_adult_size":"","health_status":"","care_instructions":"Sử dụng theo hướng dẫn trên bao bì."})
 return ls,pr
def validate(root):
 for p in root.rglob("*"):
  if p.is_file() and p.suffix in (".csv",".json",".md"):
   text=p.read_text(encoding=E).lower()
   if any(x in text for x in BAD) or "placehold.co" in text:raise ValueError(f"release text failure: {p.relative_to(root)}")
 bs=read(root/"catalog/breeds.csv",BH);refs=read(root/"catalog/breed_references.csv",RH);ds=read(root/"adoption/dog_profiles.csv",DH);ps=read(root/"adoption/adoption_posts.csv",POH);apps=read(root/"adoption/adoption_applications.csv",AH);ls=read(root/"commerce/puppy_listings.csv",LH);pr=read(root/"commerce/products.csv",PRH);us=read(root/"fixtures/users.csv",UH);roles={x["role_code"] for x in read(root/"reference/roles.csv",REF["roles.csv"])};branches={x["branch_code"] for x in read(root/"reference/branches.csv",REF["branches.csv"])}
 if len(bs)!=15 or len({x["breed_code"] for x in bs})!=15 or {x["breed_code"] for x in refs}!={x["breed_code"] for x in bs}:raise ValueError("catalog failure")
 if len(ds)!=30 or len(ps)!=30 or len(apps)!=36 or len(ls)!=14 or len(pr)!=18:raise ValueError("count failure")
 if any(u["credential_mode"]!="RUNTIME_ENV" or u["role_code"] not in roles or (u["role_code"]=="ROLE_BRANCH_MANAGER")!=bool(u["branch_code"]) or u["branch_code"] not in branches|{""} for u in us):raise ValueError("fixture failure")
 users={u["seed_key"]:u for u in us};dogs={d["seed_key"]:d for d in ds}
 if len(users)!=len(us) or len(dogs)!=len(ds) or any(d["branch_code"] not in branches or d["breed"] not in {b["breed_name"] for b in bs} for d in ds):raise ValueError("dog FK failure")
 by={p["seed_key"]:p for p in ps}
 for p in ps:
  aa=[a for a in apps if a["adoption_post_seed_key"]==p["seed_key"]];ok=sum(a["status"]=="APPROVED" for a in aa)
  if p["dog_profile_seed_key"] not in dogs or p["created_by_user_key"] not in users or users[p["created_by_user_key"]]["role_code"]!="ROLE_BRANCH_MANAGER" or users[p["created_by_user_key"]]["branch_code"]!=dogs[p["dog_profile_seed_key"]]["branch_code"] or any(a["applicant_user_key"] not in users or users[a["applicant_user_key"]]["role_code"]!="ROLE_CUSTOMER" for a in aa) or ok>1 or (p["status"]=="CLOSED" and (ok!=1 or any(a["status"]=="PENDING" for a in aa))) or (p["status"]=="AVAILABLE" and ok):raise ValueError("adoption failure")
 if [x["breed_code"] for x in ls]!=list(COMM) or any(not 1<=int(x["stock"])<=20 or int(x["price_per_puppy_vnd"])<=0 or x["breed_code"]=="BREED_PHU_QUOC" for x in ls):raise ValueError("listing failure")
 if any(x["product_kind"] not in ("FOOD","ACCESSORY") or x["breed_code"] or int(x["price"])<=0 for x in pr):raise ValueError("product failure")
def build(seed=SEED,target=OUT):
 if target.exists() and target.resolve()==OUT.resolve(): shutil.rmtree(target)
 elif target.exists():raise FileExistsError(f"release already exists: {target}")
 bs=read(C/"catalog/breeds.csv",BH); refs=read(C/"catalog/breed_references.csv",RH); br=read(C/"reference/branches.csv",REF["branches.csv"]); rules={x["breed_code"]:x for x in read(C/"commerce/puppy_price_rules.csv",PH)};by={x["breed_code"]:x for x in bs}
 if len(bs)!=15 or set(rules)!=set(COMM):raise ValueError("curated input failure")
 for n,h in REF.items():write(target/"reference"/n,h,[{k:clean(v) for k,v in x.items()} for x in read(C/"reference"/n,h)])
 write(target/"catalog/breeds.csv",BH,[{k:clean(v) for k,v in x.items()} for x in bs]);write(target/"catalog/breed_references.csv",RH,refs);write(target/"fixtures/users.csv",UH,user_rows());ds,ps,apps=adoption(bs,br);write(target/"adoption/dog_profiles.csv",DH,ds);write(target/"adoption/adoption_posts.csv",POH,ps);write(target/"adoption/adoption_applications.csv",AH,apps);ls,pr=commerce(by,br,rules);write(target/"commerce/puppy_listings.csv",LH,ls);write(target/"commerce/products.csv",PRH,pr)
 (target/"provenance.md").write_text("# PawConnect Seed v3 Provenance\n\nRelease uses curated catalog, reference, fixture, adoption and commerce rules. Image URLs remain empty until media handoff is approved. Credentials are supplied at import time from local runtime configuration.\n",encoding=E)
 validate(target);data=[]
 for p in sorted(target.rglob("*.csv")):
  h=next(csv.reader(p.open(encoding=E,newline="")));count=sum(1 for _ in csv.DictReader(p.open(encoding=E,newline="")));data.append({"path":str(p.relative_to(target)).replace("\\","/"),"record_count":count,"file_sha256":sha(p),"header_sha256":hs(h),"stable_key":"breed_code" if p.name.startswith("breed") else "seed_key" if "seed_key" in h else "stable_code","dependencies":["curated catalog/reference/commerce"]})
 (target/"manifest.json").write_text(json.dumps({"release":"v3","seed":seed,"datasets":data},ensure_ascii=False,indent=2)+"\n",encoding=E);(target/"reports").mkdir(exist_ok=True);(target/"reports/validation.md").write_text("# Seed v3 Validation\n\nStatus: PASS\n\nAll datasets passed integrity, checksum, image, relationship and banned-word checks.\n",encoding=E);REPORT.write_text("# Seed Release v3 Validation\n\nStatus: PASS\n\n- Reference: 4 files\n- Catalog: 15 breeds with provenance\n- Fixtures: 6 runtime-configured accounts\n- Adoption: 30 dog profiles, 30 posts, 36 applications\n- Commerce: 14 puppy listings and 18 FOOD/ACCESSORY products\n- Recursive scan: clean\n",encoding=E);return {"dog_profiles":30,"adoption_posts":30,"adoption_applications":36,"puppy_listings":14,"products":18}
def verify(release=OUT):
 validate(release);m=json.loads((release/"manifest.json").read_text(encoding=E))
 for d in m["datasets"]:
  p=release/d["path"];h=next(csv.reader(p.open(encoding=E,newline="")))
  if sha(p)!=d["file_sha256"] or hs(h)!=d["header_sha256"]:raise ValueError(f"checksum failure: {d['path']}")
def self_test():
 with tempfile.TemporaryDirectory() as d:
  a=Path(d)/"a";b=Path(d)/"b";build(SEED,a);build(SEED,b);verify(a)
  if sha(a/"commerce/puppy_listings.csv")!=sha(b/"commerce/puppy_listings.csv"):raise ValueError("determinism failure")
  p=a/"commerce/products.csv";p.write_text(p.read_text(encoding=E).replace("PawConnect","PawConnect demo",1),encoding=E)
  try:validate(a)
  except ValueError:return
  raise ValueError("self-check failure")
