import { extractJavaTemplates } from './java-templates';

describe('extractJavaTemplates', () => {
  const java = `
    @Override
    public void handleThing(ThingCommand command) {
        String subject = "[X] Quote \\" and dash —";
        String body = "Item \\"" + command.name() + "\\" by " + command.owner() + ".";

        sendNotification(command.eventId(), adminEmail, subject, body);
        sendNotification(command.eventId(), command.ownerEmail(), subject, body);
    }`;

  it('reads literals, escapes, field calls and recipients in order', () => {
    expect(extractJavaTemplates(java)).toEqual({
      handleThing: {
        subject: '[X] Quote " and dash —',
        body: [
          { text: 'Item "' },
          { field: 'name' },
          { text: '" by ' },
          { field: 'owner' },
          { text: '.' },
        ],
        recipients: ['adminEmail', 'ownerEmail'],
      },
    });
  });

  it('refuses a body it does not understand instead of guessing', () => {
    const odd = java.replace('command.owner()', 'format(command.owner())');
    expect(() => extractJavaTemplates(odd)).toThrow(/unexpected operand/);
  });

  it('refuses an unexpected sendNotification call', () => {
    const odd = java.replace('adminEmail, subject', 'adminEmail, "S"');
    expect(() => extractJavaTemplates(odd)).toThrow(
      /unexpected sendNotification/,
    );
  });
});
