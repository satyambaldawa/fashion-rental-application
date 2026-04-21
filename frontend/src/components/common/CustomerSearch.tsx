import { useState } from 'react'
import { AutoComplete } from 'antd'
import { customersApi } from '../../api/customers'
import { useDebounce } from '../../hooks/useDebounce'
import type { CustomerSummary } from '../../types/customer'
import { useQuery } from '@tanstack/react-query'

interface CustomerSearchProps {
  onSelect: (customer: CustomerSummary) => void
  placeholder?: string
}

export default function CustomerSearch({ onSelect, placeholder = 'Search by name or phone' }: CustomerSearchProps) {
  const [inputValue, setInputValue] = useState('')
  const debouncedInput = useDebounce(inputValue, 300)

  const isPhoneSearch = /^\d+$/.test(debouncedInput)
  const searchParam = debouncedInput.length >= 3
    ? (isPhoneSearch ? { phone: debouncedInput } : { name: debouncedInput })
    : null

  const { data: results } = useQuery({
    queryKey: ['customers', 'autocomplete', searchParam],
    queryFn: () => customersApi.search(searchParam!),
    enabled: searchParam !== null,
  })

  const options = (results ?? []).map(customer => ({
    value: customer.id,
    label: `${customer.name} — ${customer.phone}`,
    customer,
  }))

  function handleSelect(value: string) {
    const match = options.find(o => o.value === value)
    if (match) {
      setInputValue(match.label)
      onSelect(match.customer)
    }
  }

  return (
    <AutoComplete
      value={inputValue}
      options={options}
      onSearch={setInputValue}
      onSelect={handleSelect}
      placeholder={placeholder}
      style={{ width: '100%' }}
      filterOption={false}
    />
  )
}
