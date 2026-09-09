import contextlib
import io
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from evals.harness.cases import validate_corpus
from evals.harness.codex import RunConfig, _enabled_skills, build_subject_command, prepare_workspace
from evals.harness.experiment import execute_experiment, experiment_plan
from evals.harness.judge import JudgeConfig
from evals.harness.skill_source import select_skill_revision, skill_root
from evals.harness.suites import PUBLIC_SKILLS
from evals.run import main
from evals.validators.source_contract import validate
from validation.skills import validate as validate_skills

ROOT = Path(__file__).resolve().parents[2]


class DejaVuContractTest(unittest.TestCase):
    def setUp(self):
        select_skill_revision(ROOT, None)
        self.cases = validate_corpus(ROOT).cases

    def tearDown(self):
        select_skill_revision(ROOT, None)

    def test_bundle_is_portable_and_discoverable(self):
        self.assertEqual([], validate_skills())

    def test_all_skills_have_edit_review_and_restraint_coverage(self):
        self.assertEqual(15, len(self.cases))
        for skill in PUBLIC_SKILLS:
            kinds = {c.kind for c in self.cases if skill in c.target_skills}
            self.assertTrue({'direct', 'novel', 'negative'} <= kinds)
        for case in self.cases:
            self.assertTrue((case.directory / 'expectations.json').is_file())
            for skill in PUBLIC_SKILLS:
                self.assertNotIn(skill, case.prompt)  # The task doesn't reveal a route.

    def test_old_revision_isolated_and_corrupt_cache_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            skill = root / '.claude/skills/dejavu-perf-loop/SKILL.md'
            skill.parent.mkdir(parents=True)
            skill.write_text('original instructions')
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'add', '.'], cwd=root, check=True)
            subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@localhost', 'commit', '-qm', 'baseline'], cwd=root, check=True)
            skill.write_text('new instructions')
            select_skill_revision(root, 'HEAD')
            archived = skill_root(root) / 'dejavu-perf-loop/SKILL.md'
            self.assertEqual('original instructions', archived.read_text())
            self.assertEqual('new instructions', skill.read_text())
            archived.with_name('extra.md').write_text('unexpected')
            with self.assertRaisesRegex(ValueError, 'unexpected files'):
                select_skill_revision(root, 'HEAD')

    def test_plan_has_three_repetitions_and_no_live_calls(self):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            status = main(['plan', '--model', 'subject', '--reasoning', 'medium', '--judge-model', 'judge', '--judge-reasoning', 'high', '--json'])
        self.assertEqual(0, status)
        plan = json.loads(output.getvalue())
        self.assertEqual(135, plan['subject_calls'])
        self.assertEqual(270, plan['total_calls'])
        self.assertFalse(plan['execute'])

    def test_execute_rejects_a_matrix_above_call_cap_before_starting(self):
        with patch('evals.run.execute_experiment') as execute, contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            status = main(['run', '--model', 'subject', '--reasoning', 'medium', '--judge-model', 'judge', '--judge-reasoning', 'high', '--execute'])
        self.assertEqual(2, status)
        execute.assert_not_called()

    def test_each_arm_stages_only_its_skills_and_no_grader_secrets(self):
        case = next(c for c in self.cases if c.id == 'writer-augment-direct')
        with tempfile.TemporaryDirectory() as directory:
            for arm, count in [('none', 0), ('forced', 1), ('automatic', 4)]:
                workspace = prepare_workspace(case, ROOT, Path(directory)/arm, enabled_skills=_enabled_skills(case, arm, ROOT))
                self.assertEqual(count, len(list(workspace.glob('.agents/skills/*/SKILL.md'))))
                self.assertFalse(list(workspace.rglob('expectations.json')))
                self.assertFalse(list(workspace.rglob('case.json')))
                command = build_subject_command(case, arm, ROOT, workspace, RunConfig('subject', 'medium'), skill_paths=())
                self.assertNotIn('--ignore-rules', command)
                self.assertIn('web_search="disabled"', command)
                self.assertIn('sandbox_workspace_write.network_access=false', command)
                self.assertEqual(arm == 'forced', '$dejavu-test-writer' in command[-1])

    def test_external_checks_reject_no_edit_missing_artifact_and_protected_changes(self):
        case = next(c for c in self.cases if c.id == 'writer-augment-direct')
        with tempfile.TemporaryDirectory() as directory:
            workspace = prepare_workspace(case, ROOT, Path(directory)/'case')
            self.assertTrue(validate(case.id, workspace))
            test = workspace/'app/src/androidTest/CounterTest.kt'
            test.write_text(test.read_text()+'\n// Incomplete candidate: judged separately.\n')
            self.assertEqual([], validate(case.id, workspace))
            source = workspace/'app/src/main/CounterValue.kt'
            source.write_text('bad edit')
            self.assertTrue(validate(case.id, workspace))

    def test_no_change_controls_keep_edit_sandbox_but_no_allowed_writes(self):
        for case in self.cases:
            if case.kind == 'negative':
                self.assertEqual('edit', case.task_mode)
                self.assertEqual((), case.allowed_write_paths)

    def test_full_orchestration_with_fake_cli_is_explicitly_not_model_evidence(self):
        case = next(c for c in self.cases if c.id == 'perf-budget-negative')
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            executable = folder/'fake-codex'
            executable.write_text('''#!/usr/bin/env python3
import json, sys
if '--version' in sys.argv:
    print('fake-codex for harness tests only'); raise SystemExit()
if '--skip-git-repo-check' in sys.argv:
    result = {'criteria': [{'id':'criterion-1','pass':True,'evidence':'synthetic'}, {'id':'criterion-2','pass':True,'evidence':'synthetic'}], 'overall_pass':True,'rationale':'synthetic'}
else:
    result = {'summary':'synthetic fixture response','skills_used':[],'evidence':['not model evidence']}
print(json.dumps({'type':'item.completed','item':{'type':'agent_message','text':json.dumps(result)}}))
print(json.dumps({'type':'turn.completed','usage':{'input_tokens':1,'output_tokens':1}}))
''')
            executable.chmod(0o755)
            with patch('evals.harness.experiment.discover_skill_paths', return_value=()):
                paths = execute_experiment(ROOT, [case], arms=['none','forced','automatic'], repetitions=1,
                    run_config=RunConfig('fake-subject','medium'), judge_config=JudgeConfig('fake-judge','high'),
                    output_dir=folder/'output', codex_executable=str(executable))
            self.assertTrue(paths['scorecard'].is_file())
            records = json.loads(paths['results'].read_text())
            self.assertEqual(3, len(records['records']) if isinstance(records, dict) else len(records))
            for raw in (folder/'output/raw').glob('*/*/*.json'):
                payload=json.loads(raw.read_text())['payload']
                self.assertEqual(0, payload['subject']['retries'])
                self.assertEqual(0, payload['judge']['retries'])


if __name__ == '__main__':
    unittest.main()
