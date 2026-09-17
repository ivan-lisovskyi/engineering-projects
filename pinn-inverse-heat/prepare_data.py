"""Check/unpack the losslessly compressed COMSOL table without dependencies."""
import argparse
import csv
import hashlib
import io
import lzma
from pathlib import Path

EXPECTED_SHA256 = 'eb34eceee2a871294ce160f7fa9eb760c179d450a2f606d7d8e34821871b81b6'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check-only', action='store_true',
                        help='Validate data without writing sensors_T.csv.')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    data = lzma.decompress((root / 'sensors_T.csv.xz').read_bytes())
    if hashlib.sha256(data).hexdigest() != EXPECTED_SHA256:
        raise SystemExit('Checksum mismatch: table does not match the archived source.')

    rows = csv.reader(io.StringIO(data.decode('utf-8')))
    for _ in range(4):
        next(rows)
    header = next(rows)
    count = 0
    first_time = last_time = None
    for row in rows:
        if len(row) != len(header):
            raise SystemExit(f'Unexpected column count at data row {count + 1}.')
        time_value = float(row[0])
        if first_time is None:
            first_time = time_value
        last_time = time_value
        count += 1
    if (count, len(header) - 1, first_time, last_time) != (6001, 140, 0.0, 300.0):
        raise SystemExit('Unexpected dimensions or time range.')

    print('Verified: 6,001 time rows, 140 virtual sensors, time 0–300 seconds.')
    print('SHA-256:', EXPECTED_SHA256)
    if args.check_only:
        print('Check only; no files written.')
        return
    target = root / 'sensors_T.csv'
    if target.exists():
        if hashlib.sha256(target.read_bytes()).hexdigest() == EXPECTED_SHA256:
            print('Existing sensors_T.csv already matches; no changes made.')
            return
        raise SystemExit('Refusing to overwrite a different sensors_T.csv; move it first.')
    with target.open('xb') as handle:
        handle.write(data)
    print('Prepared sensors_T.csv. Open final_notebook.ipynb from this folder.')


if __name__ == '__main__':
    main()
