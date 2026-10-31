import { Link } from 'react-router-dom'
import { Card } from '../ui'

const services = [
  { to: '/pricing', title: 'Fees', note: 'Work out a fee and, if you choose, take it from the account. The daily limit is here too.' },
  { to: '/loans', title: 'Loans', note: 'Ask for a study loan. Up to €500.00 is paid in now. A larger amount waits for Sam. Repayment comes out of your balance.' },
  { to: '/checks', title: 'Books', note: 'See whether every payment has matching sides, and what has happened.' },
  { to: '/files', title: 'Files', note: 'Check that an IBAN is well formed, or bring in a practice statement.' },
  { to: '/assistant', title: 'Assistant', note: 'Ask a question about this desk. The assistant is its own service, and sending money does not use it.' },
]

export function ServicesPage() {
  return (
    <div className="grid gap-6 md:grid-cols-2">
      {services.map((service) => (
        <Link key={service.to} to={service.to}>
          <Card className="h-full p-6 transition hover:shadow-sm">
            <h2 className="text-lg font-semibold text-[#343C6A]">{service.title}</h2>
            <p className="mt-2 text-sm leading-6 text-[#718EBF]">{service.note}</p>
          </Card>
        </Link>
      ))}
    </div>
  )
}
