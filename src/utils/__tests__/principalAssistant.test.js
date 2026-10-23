import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';
import { api } from '../../api/client.js';

describe('Principal Assistant - Frontend API & Integration', () => {

  it('queryPrincipalAssistant issues POST request to /api/principal/assistant/query with token and payload', async () => {
    const originalFetch = global.fetch;
    try {
      global.fetch = async (url, options) => {
        assert.strictEqual(url, '/api/principal/assistant/query');
        assert.strictEqual(options.method, 'POST');
        assert.strictEqual(options.headers['Content-Type'], 'application/json');
        const body = JSON.parse(options.body);
        assert.strictEqual(body.question, 'Summarize staff count');

        return {
          ok: true,
          headers: { 'content-type': 'application/json' },
          json: async () => ({
            answer: 'The institution currently has 12 active staff members.',
            dataSources: ['from 12 staff records across 3 departments'],
            refusal: false
          })
        };
      };

      const res = await api.queryPrincipalAssistant('Summarize staff count');
      assert.ok(res);
      assert.ok(res.answer.includes('12 active staff members'));
      assert.strictEqual(res.dataSources.length, 1);
      assert.strictEqual(res.refusal, false);
    } finally {
      global.fetch = originalFetch;
    }
  });

  it('legacy queryKnowledge returns graceful deprecation notice without throwing', async () => {
    const res = await api.queryKnowledge('Exam ordinance');
    assert.ok(res);
    assert.ok(res.answer.includes('upgraded to the live Principal Assistant'));
    assert.strictEqual(res.citations.length, 0);
  });
});
