import json, cv2, numpy as np, sys
from reference_detect import detect_page
exp=json.load(open('detect-tests/expected.json')); ok=True
for name,e in exp.items():
    img=cv2.imread(f'detect-tests/{name}.jpg'); q=detect_page(img); diag=np.hypot(*img.shape[:2])
    if e['expected'] is None:
        r='PASS' if q is None else f'FAIL got {q.round().tolist()}'
    elif q is None: r='FAIL none'
    else:
        err=np.linalg.norm(q-np.float32(e['expected']),axis=1).max()/diag*100
        r=f"{'PASS' if err<=2.5 else 'FAIL'} max corner err {err:.2f}%"
    ok&=r.startswith('PASS'); print(f"{name:32s} {r}")
print('ALL PASS' if ok else 'SOME FAIL')
