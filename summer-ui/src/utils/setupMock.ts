export default (config: { mock?: boolean; setup: () => void }) => {
  const {
    mock =
      process.env.NODE_ENV === 'development' &&
      import.meta.env.VITE_ENABLE_MOCK !== 'false',
    setup,
  } = config;
  if (mock === false) return;
  setup();
};
