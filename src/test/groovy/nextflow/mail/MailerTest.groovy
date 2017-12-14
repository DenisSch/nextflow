package nextflow.mail
import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import java.nio.file.Files
import java.nio.file.Path

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import spock.lang.Specification
import spock.lang.Unroll

class MailerTest extends Specification {


    def 'should return config properties'() {
        when:
        def SMTP = [host: 'google.com', port: '808', user: 'foo', password: 'bar']
        def mailer = new Mailer( config: [smtp: SMTP, other: 1]  )
        def props = mailer.createProps()

        then:
        props.get('mail.smtp.user') == 'foo'
        props.get('mail.smtp.password') == 'bar'
        props.get('mail.smtp.host') == 'google.com'
        props.get('mail.smtp.port') == '808'
        !props.containsKey('mail.other')

    }


    def "sending mails using javamail"() {

        given:
        final USER = 'foo'
        final PASSWORD = 'secret'
        final EMAIL = 'yo@nextflow.com'
        final green = new GreenMail(ServerSetupTest.SMTP)
        green.setUser(EMAIL, USER, PASSWORD)
        green.start()

        def SMTP = [ host: '127.0.0.1', port: green.smtp.port, user: USER, password: PASSWORD]
        def mailer = new Mailer( config: [smtp: SMTP])

        String TO = "receiver@testmailingclass.net"
        String FROM = 'paolo@gmail.com'
        String SUBJECT = "Sending test"
        String CONTENT = "This content should be sent by the user."

        when:
        def mail = [
                to: TO,
                from: FROM,
                subject: SUBJECT,
                body: CONTENT
        ]

        mailer.send(mail)

        then:
        green.receivedMessages.size() == 1
        Message message = green.receivedMessages[0]
        message.from == [new InternetAddress(FROM)]
        message.allRecipients.contains(new InternetAddress(TO))
        message.subject == SUBJECT
        message.getContent() instanceof MimeMultipart
        (message.getContent() as MimeMultipart).getBodyPart(0).content == CONTENT

        cleanup:
        green?.stop()
    }

    def "sending mails using java with attachment"() {

        given:
        final USER = 'foo'
        final PASSWORD = 'secret'
        final EMAIL = 'yo@nextflow.com'
        final green = new GreenMail(ServerSetupTest.SMTP)
        green.setUser(EMAIL, USER, PASSWORD)
        green.start()

        def SMTP = [ host: '127.0.0.1', port: green.smtp.port, user: USER, password: PASSWORD]
        def mailer = new Mailer( config: [smtp: SMTP])

        String TO = "receiver@testmailingclass.net"
        String FROM = 'paolo@nextflow.io'
        String SUBJECT = "Sending test"
        String CONTENT = "This content should be sent by the user."
        Path ATTACH = Files.createTempFile('test', null)
        ATTACH.text = 'This is the file attachment content'

        when:
        def mail = [
                from: FROM,
                to: TO,
                subject: SUBJECT,
                body: CONTENT,
                attach: ATTACH
        ]

        mailer.send(mail)

        then:
        green.receivedMessages.size() == 1
        Message message = green.receivedMessages[0]
        message.from == [new InternetAddress(FROM)]
        message.allRecipients.contains(new InternetAddress(TO))
        message.subject == SUBJECT
        (message.getContent() as MimeMultipart).getCount() == 2
        //(message.getContent() as MimeMultipart).getBodyPart(1).getContent() == ''

        cleanup:
        ATTACH?.delete()
        green?.stop()
    }


    def 'should send with java' () {

        given:
        def mailer = Spy(Mailer)
        def MSG = Mock(MimeMessage)
        def mail = new Mail()

        when:
        mailer.config = [smtp: [host:'foo.com'] ]
        mailer.send(mail)
        then:
        0 * mailer.getSysMailer() >> null
        1 * mailer.createMimeMessage(mail) >> MSG
        1 * mailer.sendViaJavaMail(MSG) >> null

    }

    def 'should send mail with sendmail command' () {
        given:
        def mailer = Spy(Mailer)
        def MSG = Mock(MimeMessage)
        def mail = new Mail()

        when:
        mailer.send(mail)
        then:
        1 * mailer.getSysMailer() >> 'sendmail'
        1 * mailer.createMimeMessage(mail) >> MSG
        1 * mailer.sendViaSysMail(MSG) >> null
    }

    def 'should throw an exception' () {
        given:
        def mailer = Spy(Mailer)
        def mail = new Mail()
        when:
        mailer.send(mail)
        then:
        1 * mailer.getSysMailer() >> 'foo'
        thrown(IllegalArgumentException)
    }

    def 'should send mail with mail command' () {
        given:
        def mailer = Spy(Mailer)
        def MSG = Mock(MimeMessage)
        def mail = new Mail()
        when:
        mailer.send(mail)
        then:
        1 * mailer.getSysMailer() >> 'mail'
        1 * mailer.createTextMessage(mail) >> MSG
        1 * mailer.sendViaSysMail(MSG) >> null
    }


    def 'should create mime message' () {

        given:
        MimeMessage msg
        Mail mail
        when:
        mail = new Mail(from:'foo@gmail.com')
        msg = new Mailer().createMimeMessage(mail)
        then:
        msg.getFrom().size()==1
        msg.getFrom()[0].toString() == 'foo@gmail.com'

        when:
        mail = new Mail(from:'one@gmail.com, two@google.com')
        msg = new Mailer().createMimeMessage(mail)
        then:
        msg.getFrom().size()==2
        msg.getFrom()[0].toString() == 'one@gmail.com'
        msg.getFrom()[1].toString() == 'two@google.com'

        when:
        mail = new Mail(to:'foo@gmail.com, bar@google.com')
        msg = new Mailer().createMimeMessage(mail)
        then:
        msg.getRecipients(Message.RecipientType.TO).size()==2
        msg.getRecipients(Message.RecipientType.TO)[0].toString() == 'foo@gmail.com'
        msg.getRecipients(Message.RecipientType.TO)[1].toString() == 'bar@google.com'

        when:
        mail = new Mail(cc:'foo@gmail.com, bar@google.com')
        msg = new Mailer().createMimeMessage(mail)
        then:
        msg.getRecipients(Message.RecipientType.CC).size()==2
        msg.getRecipients(Message.RecipientType.CC)[0].toString() == 'foo@gmail.com'
        msg.getRecipients(Message.RecipientType.CC)[1].toString() == 'bar@google.com'

        when:
        mail = new Mail(bcc:'one@gmail.com, two@google.com')
        msg = new Mailer().createMimeMessage(mail)
        then:
        msg.getRecipients(Message.RecipientType.BCC).size()==2
        msg.getRecipients(Message.RecipientType.BCC)[0].toString() == 'one@gmail.com'
        msg.getRecipients(Message.RecipientType.BCC)[1].toString() == 'two@google.com'

        when:
        mail = new Mail(subject: 'this is a test', body: 'Ciao mondo')
        msg = new Mailer().createMimeMessage(mail)
        then:
        msg.getSubject() == 'this is a test'
        msg.getContent() instanceof MimeMultipart
        msg.getContent().getCount() == 1
        msg.getContent().getBodyPart(0).getContent() == 'Ciao mondo'
    }


    def 'should fetch config properties' () {

        given:
        def ENV = [NXF_SMTP_USER: 'jim', NXF_SMTP_PASSWORD: 'secret', NXF_SMTP_HOST: 'g.com', NXF_SMTP_PORT: '864']
        def SMTP = [host:'hola.com', user:'foo', password: 'bar', port: 234]
        Mailer mail

        when:
        mail = new Mailer(config: [smtp: SMTP])
        then:
        mail.host == 'hola.com'
        mail.user == 'foo'
        mail.password == 'bar'
        mail.port == 234

        when:
        mail = new Mailer(config: [smtp: [host: 'local', port: '999']], env: ENV)
        then:
        mail.host == 'local'
        mail.port == 999
        mail.user == 'jim'
        mail.password == 'secret'

        when:
        mail = new Mailer(env: ENV)
        then:
        mail.host == 'g.com'
        mail.port == 864
        mail.user == 'jim'
        mail.password == 'secret'
    }

    def 'should config the mailer' () {

        given:
        def CFG = '''
            mail {
              from = 'paolo@nf.com'
              subject = 'nextflow notification'

              smtp.host = 'foo.com'
              smtp.port = '43'
              smtp.user = 'jim'
              smtp.password = 'yo'
            }
            '''

        when:
        def mailer = new Mailer()
        mailer.config = new ConfigSlurper().parse(CFG).mail
        then:
        mailer.getUser() == 'jim'
        mailer.getPassword() == 'yo'
        mailer.getPort() == 43
        mailer.getHost() == 'foo.com'

    }


    def 'should capture send params' () {

        given:
        def mailer = Spy(Mailer)

        when:
        mailer.send {
            to 'paolo@dot.com'
            from 'yo@dot.com'
            subject 'This is a test'
            body 'Hello there'
        }

        then:
        1 * mailer.send(Mail.of([to: 'paolo@dot.com', from:'yo@dot.com', subject: 'This is a test', body: 'Hello there'])) >> null

    }


    def 'should strip html tags'  () {

        given:
        def mailer = new Mailer()

        expect:
        mailer.stripHtml('Hello') == 'Hello'
        mailer.stripHtml('1 < 10 > 5') == '1 < 10 > 5'
        mailer.stripHtml('<h1>1 < 5</h1>') == '1 < 5'
        mailer.stripHtml('<h1>Big title</h1><p>Hello <b>world</b></p>') == 'Big title\nHello world'
    }


    def 'should capture multiline body' () {

        given:
        def mailer = Spy(Mailer)
        def BODY = '''
            multiline
            mail
            content
            '''

        when:
        mailer.send {
            to 'you@dot.com'
            subject 'foo'
            BODY
        }

        then:
        1 * mailer.send(Mail.of([to: 'you@dot.com', subject: 'foo', body: BODY])) >> null

    }

    def 'should guess html content' () {

        given:
        def mailer = new Mailer()

        expect:
        !mailer.guessHtml('Hello')
        !mailer.guessHtml('1 < 10 > 5')
        mailer.guessHtml('<h1>1 < 5</h1>')
        mailer.guessHtml('1<br>2')
        mailer.guessHtml('<h1>Big title</h1><p>Hello <b>world</b></p>')
    }

    @Unroll
    def 'should guess mime type' () {

        given:
        def mailer = new Mailer()

        expect:
        mailer.guessMimeType(str) == type

        where:
        type            | str
        'text/plain'    | 'Hello'
        'text/plain'    | '1 < 10 > 5'
        'text/html'     | '<h1>1 < 5</h1>'
        'text/html'     | '1<br>2'
        'text/html'     | '<h1>Big title</h1><p>Hello <b>world</b></p>'

    }

}
