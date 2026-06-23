"""
One-time migration script for existing PTA-imported students.

Fixes the data gap where PTA students have:
  - student_profile records (with user_id = NULL)
  - class_member records
But are missing:
  - tap_user accounts (can't login)
  - class_student records (can't be queried by legacy paths)

After running this script, PTA students will be able to:
  1. Login with student_no / student_no (default password)
  2. See their experiment data in the student portal

Usage:
  python migrate_pta_students.py [--dry-run]

Environment variables (same as sync_to_db.py):
  DB_HOST, DB_PORT, DB_USER/DB_USERNAME, DB_PASSWORD/DB_PASS, DB_NAME
"""

import os
import sys
import argparse

import pymysql

try:
    import bcrypt
except ImportError:
    print("ERROR: bcrypt is required. Install it with: pip install bcrypt")
    sys.exit(1)


def get_db():
    return pymysql.connect(
        host=os.getenv("DB_HOST", "localhost"),
        port=int(os.getenv("DB_PORT", "3306")),
        user=os.getenv("DB_USER") or os.getenv("DB_USERNAME", "root"),
        password=os.getenv("DB_PASSWORD") or os.getenv("DB_PASS", ""),
        database=os.getenv("DB_NAME", "ptadatabase"),
        charset="utf8mb4",
        autocommit=False,
    )


def main():
    parser = argparse.ArgumentParser(description="Migrate existing PTA students")
    parser.add_argument("--dry-run", action="store_true", help="Preview changes without committing")
    args = parser.parse_args()

    conn = get_db()
    cursor = conn.cursor()

    # ──────────────────────────────────────────────────────────
    # Step 1: Find student_profiles without tap_user accounts
    # ──────────────────────────────────────────────────────────
    cursor.execute(
        """
        SELECT sp.id, sp.student_no, sp.real_name
        FROM student_profile sp
        WHERE sp.user_id IS NULL
          AND sp.status = 'ACTIVE'
        ORDER BY sp.id
        """
    )
    profiles_without_user = cursor.fetchall()
    print(f"\n{'='*60}")
    print(f"Step 1: student_profile records without tap_user")
    print(f"{'='*60}")
    print(f"Found {len(profiles_without_user)} student(s) needing tap_user accounts\n")

    created_users = 0
    bound_profiles = 0

    for sp_id, student_no, real_name in profiles_without_user:
        # Check if tap_user already exists with this username
        cursor.execute("SELECT id FROM tap_user WHERE username = %s", (student_no,))
        tu_row = cursor.fetchone()

        if tu_row:
            tap_user_id = tu_row[0]
            print(f"  [EXISTING] tap_user({tap_user_id}) already exists for {student_no}")
        else:
            # Generate BCrypt hash
            default_password = str(student_no)
            password_hash = bcrypt.hashpw(
                default_password.encode("utf-8"), bcrypt.gensalt()
            ).decode("utf-8")

            if args.dry_run:
                print(f"  [DRY-RUN] Would create tap_user for {student_no} ({real_name})")
                continue

            cursor.execute(
                """
                INSERT INTO tap_user (username, display_name, password_hash, role, enabled, created_at, updated_at)
                VALUES (%s, %s, %s, 'STUDENT', TRUE, NOW(3), NOW(3))
                """,
                (student_no, real_name, password_hash),
            )
            tap_user_id = cursor.lastrowid
            created_users += 1
            print(f"  [CREATED] tap_user({tap_user_id}) for {student_no} ({real_name})")

        # Bind student_profile.user_id
        if not args.dry_run and tap_user_id:
            cursor.execute(
                "UPDATE student_profile SET user_id = %s WHERE id = %s AND user_id IS NULL",
                (tap_user_id, sp_id),
            )
            if cursor.rowcount > 0:
                bound_profiles += 1

    # ──────────────────────────────────────────────────────────
    # Step 2: Sync class_member → class_student
    # ──────────────────────────────────────────────────────────
    cursor.execute(
        """
        SELECT cm.class_id, sp.student_no, sp.real_name
        FROM class_member cm
        JOIN student_profile sp ON sp.id = cm.student_id
        LEFT JOIN class_student cs ON cs.class_id = cm.class_id AND cs.student_num = sp.student_no
        WHERE cs.id IS NULL
          AND cm.member_status = 'ACTIVE'
        ORDER BY cm.class_id, sp.student_no
        """
    )
    missing_class_students = cursor.fetchall()
    print(f"\n{'='*60}")
    print(f"Step 2: class_member records missing from class_student")
    print(f"{'='*60}")
    print(f"Found {len(missing_class_students)} missing class_student record(s)\n")

    synced_cs = 0

    for class_id, student_no, real_name in missing_class_students:
        if args.dry_run:
            print(f"  [DRY-RUN] Would insert class_student: class={class_id}, student={student_no}")
            continue

        cursor.execute(
            """
            INSERT INTO class_student (class_id, student_num, student_name, joined_at)
            VALUES (%s, %s, %s, NOW())
            ON DUPLICATE KEY UPDATE
              student_name = COALESCE(NULLIF(VALUES(student_name), ''), class_student.student_name)
            """,
            (class_id, student_no, real_name),
        )
        synced_cs += cursor.rowcount
        print(f"  [CREATED] class_student: class_id={class_id}, student_num={student_no}")

    # ──────────────────────────────────────────────────────────
    # Commit or rollback
    # ──────────────────────────────────────────────────────────
    if args.dry_run:
        print(f"\n[DRY-RUN] No changes committed. Rerun without --dry-run to apply.")
    else:
        conn.commit()
        print(f"\n{'='*60}")
        print(f"Migration Summary")
        print(f"{'='*60}")
        print(f"  tap_user accounts created:  {created_users}")
        print(f"  student_profiles bound:     {bound_profiles}")
        print(f"  class_student records added: {synced_cs}")
        print(f"Done! All changes committed.")

    cursor.close()
    conn.close()


if __name__ == "__main__":
    main()
