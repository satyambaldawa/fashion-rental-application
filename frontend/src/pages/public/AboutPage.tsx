import { Typography } from 'antd'
import PageHeader from '../../components/common/PageHeader'

const { Paragraph } = Typography

export default function AboutPage() {
  return (
    <>
      <PageHeader label="About" title="About" accent="Manisha's Drapery" />
      <Paragraph type="secondary">
        Our story is coming soon. Check back shortly to learn more about Manisha's Drapery.
      </Paragraph>
    </>
  )
}
